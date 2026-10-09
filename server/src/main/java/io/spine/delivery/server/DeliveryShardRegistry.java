/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package io.spine.delivery.server;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Durations;
import io.spine.delivery.rejection.ShardAlreadyPickedUp;
import io.spine.delivery.storage.CasOutcome;
import io.spine.delivery.storage.ShardSessionStore;
import io.spine.delivery.storage.Stored;
import io.spine.logging.WithLogging;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.ShardProcessingSession;
import io.spine.server.delivery.ShardSessionRecord;
import io.spine.server.delivery.WorkerId;
import kotlin.uuid.Uuid;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.protobuf.util.Durations.checkNotNegative;
import static com.google.protobuf.util.Durations.compare;
import static com.google.protobuf.util.Timestamps.between;
import static io.spine.base.Time.currentTime;
import static io.spine.delivery.storage.Shards.tag;
import static java.lang.String.format;
import static java.lang.System.lineSeparator;

/**
 * The registry of the shard indexes along with the worker identifiers,
 * which process the messages corresponding to each index.
 *
 * <p>The session records are kept in a {@link ShardSessionStore}, which several Delivery
 * servers may share. Every read-then-write of a record is a compare-and-set against
 * the record that was read, so each change is atomic across all those servers.
 */
public final class DeliveryShardRegistry implements WithLogging {

    /**
     * How many times an operation tries to change a record before it gives up.
     *
     * <p>An attempt fails when another call changed the record after it was read, or when
     * the write itself failed. After a conflict, a pick usually finds the shard taken and
     * stops without writing, so a call needs more than a couple of attempts only when
     * something is wrong, such as a store whose writes keep failing. Sixteen attempts are
     * a generous bound, which keeps such a call from trying forever. The number is a choice,
     * not a measurement.
     */
    @VisibleForTesting
    static final int MAX_ATTEMPTS = 16;

    /**
     * The store of the session records.
     */
    private final ShardSessionStore store;

    /**
     * How long a worker may hold a shard before its session is stale, or zero if a session
     * never becomes stale.
     */
    private final Duration processingTimeout;

    /**
     * Creates a new {@code DeliveryShardRegistry} backed by the given store.
     *
     * <p>The given {@code processingTimeout} is used to determine whether a session is stale.
     * Stale sessions are released automatically. Pass {@link Durations#ZERO Durations.ZERO}
     * to disable the stale-check. In this case all picked up sessions will always be considered
     * active until explicitly released by a worker.
     */
    public DeliveryShardRegistry(ShardSessionStore store, Duration processingTimeout) {
        super();
        this.store = checkNotNull(store);
        this.processingTimeout = checkNotNegative(processingTimeout);
    }

    /**
     * Picks up the shard at a given index to process.
     *
     * <p>This action is exclusive across all the servers that share the store, with
     * the exception below: a single shard may be served by a single worker at a given moment
     * of time.
     *
     * <p>In case of a successful operation, an instance of {@link ShardProcessingSession}
     * is returned. There are two options when it is successful:
     *
     * <ol>
     *     <li>There is no worker associated with the requested shard.
     *     <li>The requested shard is already being processed by some worker, but its
     *     processing time reached {@link #processingTimeout}. Such a session is considered
     *     stale and released automatically.
     * </ol>
     *
     * <p>In case the shard at a given index is already picked up by a worker and
     * has not reached {@linkplain #processingTimeout processing timeout},
     * an {@link ShardAlreadyPickedUp} is thrown.
     *
     * <p>The exclusivity has one exception: a store shared by servers that a network split
     * divides may let each part of them pick the same shard.
     *
     * @param index
     *         the index of the shard to pick up for processing
     * @param worker
     *         the identifier of the worker for which to pick the shard
     * @return the session of shard processing
     * @throws ShardAlreadyPickedUp
     *         if the shard is already picked up, even by the same worker
     */
    public ShardProcessingSession pickUp(ShardIndex index, WorkerId worker)
            throws ShardAlreadyPickedUp {
        var now = currentTime();
        var picked = ShardSessionRecord.newBuilder()
                .setIndex(index)
                .setWorker(worker)
                .setWhenLastPicked(now)
                .build();
        // Every attempt writes the same record with the same write ID, which no other call
        // has. So a record found with this write ID is this call's own write, applied by
        // an attempt whose outcome was not known.
        var writeId = Uuid.Companion.random();
        // The record of the session that holds the shard, or `null` if this call picked it.
        @Nullable ShardSessionRecord holder = update(index, writeId, store.read(index), current -> {
            if (current == null) {
                return Decision.write(picked, null);
            }
            if (current.getWriteId().equals(writeId)) {
                return Decision.done(null);
            }
            var record = current.getRecord();
            if (hasWorker(record)) {
                if (!isStale(record, now)) {
                    return Decision.done(record);
                }
                logStale(record, now);
            }
            return Decision.write(picked, null);
        });
        if (holder != null) {
            throw ShardAlreadyPickedUp.newBuilder()
                    .setShard(index)
                    .setWorker(holder.getWorker())
                    .setWhenPicked(holder.getWhenLastPicked())
                    .build();
        }
        return new DeliveryShardSession(picked);
    }

    /**
     * Releases the shard under the given index.
     *
     * <p>Clears the worker of the shard's record, if the record exists, even if its worker
     * is already cleared.
     */
    public void releaseShard(ShardIndex index) {
        update(index, Uuid.Companion.random(), store.read(index),
               current -> current == null
                          ? Decision.done(null)
                          : Decision.write(cleared(current), null));
    }

    /**
     * Clears up the recorded {@code WorkerId}s from the session records if there was no activity
     * for longer than the passed {@code inactivityPeriod}.
     *
     * <p>It may be handy if an instance of the application hangs or gets killed, so that it
     * is not able to complete the session in a conventional way.
     *
     * @return the released records as they were before the release, each reported by
     *         the only call whose write released it
     */
    public ImmutableSet<ShardSessionRecord> releaseInactiveSessions(Duration inactivityPeriod) {
        checkNotNull(inactivityPeriod);
        var now = currentTime();
        var result = ImmutableSet.<ShardSessionRecord>builder();
        for (var stored : store.readAll()) {
            var index = stored.getRecord().getIndex();
            var released = update(index, Uuid.Companion.random(), stored, current -> {
                if (current == null || !isInactive(current.getRecord(), inactivityPeriod, now)) {
                    return Decision.done(null);
                }
                return Decision.write(cleared(current), current.getRecord());
            });
            if (released != null) {
                result.add(released);
            }
        }
        return result.build();
    }

    /**
     * Repeats the decision on the current record of the shard and its write, until
     * the decision needs no write, or the write is applied.
     *
     * <p>After a conflict, decides again on the record that the store reports as current.
     * After a failed write, which may or may not have been applied, reads the record again
     * and decides again on it. A failure of that read fails the call.
     *
     * @param <T>
     *         the type of the result
     * @param index
     *         the shard of the record
     * @param writeId
     *         the ID with which every attempt writes, which no other call uses
     * @param initial
     *         the record to decide on first
     * @param decide
     *         decides what to do with the current record of the shard
     * @return the result of the last decision
     * @throws IllegalStateException
     *         if the record keeps changing for {@link #MAX_ATTEMPTS} attempts
     */
    private <T> T update(ShardIndex index,
                         Uuid writeId,
                         @Nullable Stored initial,
                         Function<@Nullable Stored, Decision<T>> decide) {
        var current = initial;
        for (var attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            var decision = decide.apply(current);
            var replacement = decision.replacement;
            if (replacement == null) {
                return decision.result;
            }
            try {
                var outcome = store.compareAndSet(index, current, replacement, writeId);
                if (outcome instanceof CasOutcome.Conflict conflict) {
                    current = conflict.getCurrent();
                } else {
                    return decision.result;
                }
            } catch (RuntimeException e) {
                logger().atWarning().withCause(e).log(() -> format(
                        "Writing the session of the shard `%s` failed. Reading it again.",
                        tag(index)));
                current = store.read(index);
            }
        }
        var message = format("The session of the shard `%s` kept changing for %d attempts.",
                             tag(index), MAX_ATTEMPTS);
        logger().atError().log(() -> message);
        throw new IllegalStateException(message);
    }

    /**
     * Returns the stored record without its worker.
     */
    private static ShardSessionRecord cleared(Stored stored) {
        return stored.getRecord()
                     .toBuilder()
                     .clearWorker()
                     .build();
    }

    /**
     * Logs that the stale session is released, with how long it lasted.
     */
    private void logStale(ShardSessionRecord session, Timestamp now) {
        var processingTime = between(session.getWhenLastPicked(), now);
        var logMessage = String.join(
                lineSeparator(),
                format("Shard %d reached the processing timeout and was released automatically.",
                       session.getIndex().getIndex()),
                format("Processing time: %d seconds.", processingTime.getSeconds()),
                format("Configured threshold: %d seconds.", processingTimeout.getSeconds()));
        logger().atWarning().log(() -> logMessage);
    }

    /**
     * Tells whether strictly more than the processing timeout has passed since the session
     * started, and the timeout is set.
     */
    private boolean isStale(ShardSessionRecord session, Timestamp now) {
        if (processingTimeout.getSeconds() == 0) {
            return false;
        }
        var elapsed = between(session.getWhenLastPicked(), now);
        return compare(elapsed, processingTimeout) > 0;
    }

    /**
     * Tells whether the session has a worker, and has lasted at least the given period.
     */
    private static boolean isInactive(ShardSessionRecord session,
                                      Duration inactivityPeriod,
                                      Timestamp now) {
        if (!session.hasWorker()) {
            return false;
        }
        var elapsed = between(session.getWhenLastPicked(), now);
        return compare(elapsed, inactivityPeriod) >= 0;
    }

    /**
     * Tells whether a worker holds the shard of the record.
     */
    private static boolean hasWorker(ShardSessionRecord record) {
        return !WorkerId.getDefaultInstance()
                        .equals(record.getWorker());
    }

    /**
     * What to do with the current record of a shard.
     *
     * @param <T>
     *         the type of the result of the operation
     */
    private static final class Decision<T> {

        /**
         * The record to write, or {@code null} if nothing is to be written.
         */
        private final @Nullable ShardSessionRecord replacement;

        /**
         * The result of the operation, once the decision is carried out.
         */
        private final T result;

        /**
         * Creates the decision to write the replacement, if any, and finish with the result.
         */
        private Decision(@Nullable ShardSessionRecord replacement, T result) {
            this.replacement = replacement;
            this.result = result;
        }

        /**
         * Writes the replacement, and then finishes with the given result.
         */
        static <T> Decision<T> write(ShardSessionRecord replacement, T result) {
            return new Decision<>(replacement, result);
        }

        /**
         * Finishes with the given result, without a write.
         */
        static <T> Decision<T> done(T result) {
            return new Decision<>(null, result);
        }
    }

    /**
     * Implementation of the shard processing session, completed by releasing its shard.
     */
    public final class DeliveryShardSession extends ShardProcessingSession {

        /**
         * Creates the session described by the given record.
         */
        private DeliveryShardSession(ShardSessionRecord record) {
            super(record);
        }

        /**
         * Releases the shard of the session.
         */
        @Override
        protected void complete() {
            releaseShard(shardIndex());
        }
    }
}
