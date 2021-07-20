/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import com.google.protobuf.Duration;
import io.spine.server.NodeId;
import io.spine.server.delivery.AbstractWorkRegistry;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.ShardProcessingSession;
import io.spine.server.delivery.ShardSessionRecord;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import static com.google.common.collect.Iterators.unmodifiableIterator;
import static com.google.common.collect.Maps.newConcurrentMap;
import static java.util.Objects.requireNonNull;

/**
 * An {@link io.spine.server.delivery.memory.InMemoryShardedWorkRegistry
 * InMemoryShardedWorkRegistry} with some more API endpoints exposes to public.
 */
public class ExtendedShardRegistry extends AbstractWorkRegistry {

    private final Map<ShardIndex, ShardSessionRecord> workByNode = newConcurrentMap();

    @Override
    public synchronized Optional<ShardProcessingSession> pickUp(ShardIndex index, NodeId nodeId) {
        return super.pickUp(index, nodeId);
    }

    @Override
    public synchronized Iterable<ShardIndex> releaseExpiredSessions(Duration inactivityPeriod) {
        return super.releaseExpiredSessions(inactivityPeriod);
    }

    @Override
    protected synchronized void clearNode(ShardSessionRecord session) {
        super.clearNode(session);
    }

    @Override
    protected Iterator<ShardSessionRecord> allRecords() {
        return unmodifiableIterator(workByNode.values().iterator());
    }

    @Override
    protected void write(ShardSessionRecord session) {
        workByNode.put(session.getIndex(), session);
    }

    @Override
    protected Optional<ShardSessionRecord> find(ShardIndex index) {
        return Optional.ofNullable(workByNode.get(index));
    }

    @Override
    protected ShardProcessingSession asSession(ShardSessionRecord record) {
        return new InMemoryShardSession(record);
    }

    /**
     * Releases the shard under the given index.
     */
    public synchronized void releaseShard(ShardIndex index) {
        ShardSessionRecord record = workByNode.get(index);
        if(record != null) {
            clearNode(record);
        }
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
            ShardSessionRecord record = workByNode.get(shardIndex());
            requireNonNull(record);
            // Clear the node ID value and release the session.
            clearNode(record);
        }
    }
}
