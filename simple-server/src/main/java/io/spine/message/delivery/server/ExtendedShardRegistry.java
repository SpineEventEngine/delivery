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
import io.spine.server.NodeId;
import io.spine.server.delivery.AbstractWorkRegistry;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.ShardProcessingSession;
import io.spine.server.delivery.ShardSessionRecord;
import io.spine.server.storage.StorageFactory;

import java.util.Iterator;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.protobuf.util.Timestamps.between;
import static io.spine.base.Time.currentTime;

/**
 * A {@link ShardRegistryStorage}-baked {@link io.spine.server.delivery.ShardedWorkRegistry
 * ShardedWorkRegistry}  with some more API endpoints exposes to public.
 */
public final class ExtendedShardRegistry extends AbstractWorkRegistry {

    private final ShardRegistryStorage storage;

    /**
     * Creates a new {@code ExtendedShardRegistry} backed by {@link ShardRegistryStorage} created
     * from the configured {@code factory}.
     */
    public ExtendedShardRegistry(StorageFactory factory) {
        super();
        checkNotNull(factory);
        this.storage = new ShardRegistryStorage(factory);
    }

    @Override
    public synchronized Optional<ShardProcessingSession> pickUp(ShardIndex index, NodeId nodeId) {
        return super.pickUp(index, nodeId);
    }

    @Override
    public synchronized Iterable<ShardIndex> releaseExpiredSessions(Duration inactivityPeriod) {
        return super.releaseExpiredSessions(inactivityPeriod);
    }

    /**
     * Clears up the recorded {@code NodeId}s from the session records if there was no activity
     * for longer than passed {@code inactivityPeriod}.
     *
     * <p>It may be handy if an application node hangs or gets killed — so that it is not able
     * to complete the session in a conventional way.
     *
     * @implNote A copy of the {@link #releaseExpiredSessions(Duration)} which returns the
     *         whole {@code ShardSessionRecord} instead of the {@code ShardIndex}.
     * @see #releaseExpiredSessions(Duration)
     */
    public synchronized ImmutableSet<ShardSessionRecord>
    releaseInactiveSessions(Duration inactivityPeriod) {
        checkNotNull(inactivityPeriod);
        ImmutableSet.Builder<ShardSessionRecord> resultBuilder = ImmutableSet.builder();
        allRecords().forEachRemaining(record -> {
            if (record.hasPickedBy()) {
                Timestamp whenPicked = record.getWhenLastPicked();
                Duration elapsed = between(whenPicked, currentTime());

                int comparison = Durations.compare(elapsed, inactivityPeriod);
                if (comparison >= 0) {
                    clearNode(record);
                    resultBuilder.add(record);
                }
            }
        });
        return resultBuilder.build();
    }

    @Override
    protected synchronized void clearNode(ShardSessionRecord session) {
        super.clearNode(session);
    }

    @Override
    protected Iterator<ShardSessionRecord> allRecords() {
        return storage.readAll();
    }

    @Override
    protected void write(ShardSessionRecord session) {
        storage.write(session.getIndex(), session);
    }

    @Override
    protected Optional<ShardSessionRecord> find(ShardIndex index) {
        return storage.read(index);
    }

    @Override
    protected ShardProcessingSession asSession(ShardSessionRecord record) {
        return new InMemoryShardSession(record);
    }

    /**
     * Releases the shard under the given index.
     */
    public synchronized void releaseShard(ShardIndex index) {
        storage.read(index)
               .ifPresent(this::clearNode);
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
            record.ifPresent(ExtendedShardRegistry.this::clearNode);
        }
    }
}
