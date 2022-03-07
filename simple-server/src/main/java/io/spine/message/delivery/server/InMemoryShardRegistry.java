/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Durations;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.ShardProcessingSession;
import io.spine.server.delivery.ShardSessionRecord;
import io.spine.server.delivery.WorkerId;
import io.spine.server.storage.StorageFactory;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.protobuf.util.Timestamps.between;
import static io.spine.base.Time.currentTime;

/**
 * A {@link ShardRegistryStorage}-baked {@link io.spine.server.delivery.ShardedWorkRegistry
 * ShardedWorkRegistry}  with some more API endpoints exposes to public.
 */
public final class InMemoryShardRegistry {

    private final ShardRegistryStorage storage;

    /**
     * Creates a new {@code ExtendedShardRegistry} backed by {@link ShardRegistryStorage} created
     * from the configured {@code factory}.
     */
    public InMemoryShardRegistry(StorageFactory factory) {
        super();
        checkNotNull(factory);
        this.storage = new ShardRegistryStorage(factory);
    }

    public synchronized Optional<ShardProcessingSession> pickUp(ShardIndex index,
                                                                WorkerId workerId) {
        var optionalRecord = find(index);
        if (optionalRecord.isEmpty()) {
            var newRecord = createRecord(index, workerId);
            return Optional.of(asSession(newRecord));
        }

        var record = optionalRecord.get();
        if (hasWorker(record)) {
            return Optional.empty();
        }

        var updatedRecord = updateNode(record, workerId);
        return Optional.of(asSession(updatedRecord));
    }

    private static boolean hasWorker(ShardSessionRecord record) {
        return !WorkerId.getDefaultInstance().equals(record.getWorker());
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
        return new InMemoryShardSession(record);
    }

    private void clearWorker(ShardSessionRecord session) {
        var record = session.toBuilder()
                .clearWorker()
                .build();
        storage.write(session.getIndex(), record);
    }

    private ShardSessionRecord updateNode(ShardSessionRecord record, WorkerId worker) {
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
     * Clears up the recorded {@code NodeId}s from the session records if there was no activity
     * for longer than passed {@code inactivityPeriod}.
     *
     * <p>It may be handy if an application node hangs or gets killed — so that it is not able
     * to complete the session in a conventional way.
     */
    public synchronized ImmutableSet<ShardSessionRecord>
    releaseInactiveSessions(Duration inactivityPeriod) {
        checkNotNull(inactivityPeriod);
        ImmutableSet.Builder<ShardSessionRecord> resultBuilder = ImmutableSet.builder();
        storage.readAll().forEachRemaining(record -> {
            if (record.hasWorker()) {
                Timestamp whenPicked = record.getWhenLastPicked();
                Duration elapsed = between(whenPicked, currentTime());

                int comparison = Durations.compare(elapsed, inactivityPeriod);
                if (comparison >= 0) {
                    clearWorker(record);
                    resultBuilder.add(record);
                }
            }
        });
        return resultBuilder.build();
    }

    /**
     * Implementation of shard processing session, based on in-memory storage mechanism.
     */
    public class InMemoryShardSession extends ShardProcessingSession {

        private InMemoryShardSession(ShardSessionRecord record) {
            super(record);
        }

        @Override
        protected void complete() {
            Optional<ShardSessionRecord> record = storage.read(shardIndex());
            record.ifPresent(InMemoryShardRegistry.this::clearWorker);
        }
    }
}
