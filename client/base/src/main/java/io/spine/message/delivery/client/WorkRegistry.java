/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Duration;
import io.spine.logging.Logging;
import io.spine.message.delivery.event.ExpiredSession;
import io.spine.message.delivery.event.ExpiredSessionsReleased;
import io.spine.server.NodeId;
import io.spine.server.delivery.PickUpOutcome;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.ShardSessionRecord;
import io.spine.server.delivery.ShardedWorkRegistry;
import io.spine.server.delivery.WorkerId;

import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.util.Preconditions2.checkNotDefaultArg;

/**
 * A work registry backed by the remote {@link SessionRegistryClient}.
 */
public final class WorkRegistry implements ShardedWorkRegistry, Logging {

    private final Supplier<? extends SessionRegistryClient> client;

    /**
     * Creates a new registry instance with the supplied {@code client}.
     */
    public WorkRegistry(Supplier<? extends SessionRegistryClient> client) {
        this.client = checkNotNull(client);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The ID of the worker that tries to pick a shard is the concatenation of the
     * provided node ID and current thread ID.
     */
    @Override
    public PickUpOutcome pickUp(ShardIndex index, NodeId nodeId) {
        checkNotDefaultArg(index);
        checkNotDefaultArg(nodeId);
        return client
                .get()
                .pickUpShard(index, workerId(nodeId));
    }

    @Override
    public void release(ShardSessionRecord session) {
        releaseShard(session);
    }

    private static WorkerId workerId(NodeId nodeId) {
        String threadId = String.valueOf(Thread.currentThread()
                                               .getId());
        return WorkerId.newBuilder()
                .setNodeId(nodeId)
                .setValue(threadId)
                .vBuild();
    }

    private void releaseShard(ShardSessionRecord session) {
        WorkerId worker = session.getWorker();
        ShardIndex shard = session.getIndex();
        client.get()
              .releaseShard(shard, worker);
    }

    @Override
    public Iterable<ShardIndex> releaseExpiredSessions(Duration inactivityPeriod) {
        checkNotDefaultArg(inactivityPeriod);
        ExpiredSessionsReleased sessionsReleased =
                client.get()
                      .releaseExpiredSessions(inactivityPeriod);
        return sessionsReleased
                .getShardList()
                .stream()
                .map(ExpiredSession::getShard)
                .collect(ImmutableList.toImmutableList());
    }
}
