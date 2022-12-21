/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Durations;
import io.spine.logging.Logging;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.ShardProcessingSession;
import io.spine.server.delivery.ShardSessionRecord;
import io.spine.server.delivery.WorkerId;
import io.spine.server.storage.StorageFactory;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.protobuf.util.Durations.checkNotNegative;
import static com.google.protobuf.util.Durations.compare;
import static com.google.protobuf.util.Timestamps.between;
import static io.spine.base.Time.currentTime;
import static java.lang.String.format;
import static java.lang.System.lineSeparator;

/**
 * The registry of the shard indexes along with the worker identifiers,
 * which process the messages corresponding to each index.
 *
 * <p>All picked shards are stored in the {@link ShardRegistryStorage}.
 */
public final class LiquorShardRegistry implements Logging {

    private final ShardRegistryStorage storage;
    private final Duration processingTimeout;

    /**
     * Creates a new {@code LiquorShardRegistry} backed by {@link ShardRegistryStorage}.
     *
     * <p>The given {@code processingTimeout} is used to determine whether a session is stale.
     * Stale sessions are released automatically. Pass {@link Durations#ZERO Durations.ZERO}
     * to disable the stale-check. In this case all picked up sessions will always be considered
     * active until explicitly released by a worker.
     */
    public LiquorShardRegistry(StorageFactory factory, Duration processingTimeout) {
        super();
        checkNotNull(factory);
        this.storage = new ShardRegistryStorage(factory);
        this.processingTimeout = checkNotNegative(processingTimeout);
    }

    /**
     * Picks up the shard at a given index to process.
     *
     * <p>This action is intended to be exclusive, i.e., a single shard may be served
     * by a single worker at a given moment of time.
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
     * <p> A worker that obtained the session should perform the desired actions with
     * the sharded messages and then {@linkplain LiquorShardSession#complete() complete}
     * the session.
     *
     * <p>In case the shard at a given index is already picked up by a worker and
     * has not reached {@linkplain #processingTimeout processing timeout},
     * an {@link Optional#empty() Optional.empty()} is returned.
     *
     * @param index
     *         the index of the shard to pick up for processing
     * @param worker
     *         the identifier of the worker for which to pick the shard
     * @return the session of shard processing,
     *         or {@code Optional.empty()} if the shard is not available
     */
    public synchronized Optional<ShardProcessingSession> pickUp(ShardIndex index,
                                                                WorkerId worker) {
        var optionalRecord = find(index);
        if (optionalRecord.isEmpty()) {
            var newRecord = createRecord(index, worker);
            return Optional.of(asSession(newRecord));
        }

        var record = optionalRecord.get();
        if (hasWorker(record)) {
            if (isStale(record)) {
                logStale(record);
            } else {
                return Optional.empty();
            }
        }

        var updatedSession = updateWorker(record, worker);
        return Optional.of(asSession(updatedSession));
    }

    private void logStale(ShardSessionRecord session) {
        String mainMsg = format("Shard %d reached the processing timeout and was" +
                                        " released automatically.", session.getIndex().getIndex());
        Duration processingTime = between(session.getWhenLastPicked(), currentTime());
        ImmutableList<String> logStatements = ImmutableList.<String>builder()
                .add(mainMsg)
                .add(format("Processing time: %d seconds.", processingTime.getSeconds()))
                .add(format("Configured threshold: %d seconds.", processingTimeout.getSeconds()))
                .build();
        String logMessage = Joiner.on(lineSeparator()).join(logStatements);
        _warn().log(logMessage);
    }

    private boolean isStale(ShardSessionRecord session) {
        if (processingTimeout.getSeconds() == 0) {
            return false;
        }
        Timestamp whenPicked = session.getWhenLastPicked();
        Duration elapsed = between(whenPicked, currentTime());
        int comparison = compare(elapsed, processingTimeout);
        boolean result = comparison > 0;
        return result;
    }

    private static boolean hasWorker(ShardSessionRecord record) {
        return !WorkerId.getDefaultInstance()
                        .equals(record.getWorker());
    }

    private Optional<ShardSessionRecord> find(ShardIndex index) {
        return storage.read(index);
    }

    private ShardSessionRecord createRecord(ShardIndex index, WorkerId worker) {
        var newRecord = ShardSessionRecord.newBuilder()
                .setIndex(index)
                .setWorker(worker)
                .setWhenLastPicked(currentTime())
                .vBuild();
        storage.write(index, newRecord);
        return newRecord;
    }

    private ShardProcessingSession asSession(ShardSessionRecord record) {
        return new LiquorShardSession(record);
    }

    private void clearWorker(ShardSessionRecord session) {
        var record = session.toBuilder()
                .clearWorker()
                .build();
        storage.write(session.getIndex(), record);
    }

    private ShardSessionRecord updateWorker(ShardSessionRecord record, WorkerId worker) {
        var updatedRecord = record.toBuilder()
                .setWorker(worker)
                .setWhenLastPicked(currentTime())
                .build();
        storage.write(updatedRecord.getIndex(), updatedRecord);
        return updatedRecord;
    }

    /**
     * Releases the shard under the given index.
     */
    public synchronized void releaseShard(ShardIndex index) {
        storage.read(index)
               .ifPresent(this::clearWorker);
    }

    /**
     * Clears up the recorded {@code WorkerId}s from the session records if there was no activity
     * for longer than passed {@code inactivityPeriod}.
     *
     * <p>It may be handy if an application node hangs or gets killed — so that it is not able
     * to complete the session in a conventional way.
     */
    public synchronized ImmutableSet<ShardSessionRecord>
    releaseInactiveSessions(Duration inactivityPeriod) {
        checkNotNull(inactivityPeriod);
        ImmutableSet.Builder<ShardSessionRecord> resultBuilder = ImmutableSet.builder();
        storage.readAll()
               .forEachRemaining(record -> {
                   if (record.hasWorker()) {
                       Timestamp whenPicked = record.getWhenLastPicked();
                       Duration elapsed = between(whenPicked, currentTime());

                       int comparison = compare(elapsed, inactivityPeriod);
                       if (comparison >= 0) {
                           clearWorker(record);
                           resultBuilder.add(record);
                       }
                   }
               });
        return resultBuilder.build();
    }

    /**
     * Implementation of shard processing session, based on
     * {@link ShardRegistryStorage} storage mechanism.
     */
    public class LiquorShardSession extends ShardProcessingSession {

        private LiquorShardSession(ShardSessionRecord record) {
            super(record);
        }

        @Override
        protected void complete() {
            Optional<ShardSessionRecord> record = storage.read(shardIndex());
            record.ifPresent(LiquorShardRegistry.this::clearWorker);
        }
    }
}
