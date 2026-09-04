/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.spine.delivery.client;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Duration;
import io.spine.delivery.event.ExpiredSession;
import io.spine.delivery.event.ExpiredSessionsReleased;
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
public final class WorkRegistry implements ShardedWorkRegistry {

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
     * provided node ID and the current thread ID.
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
        var threadId = String.valueOf(Thread.currentThread()
                                            .getId());
        return WorkerId.newBuilder()
                .setNodeId(nodeId)
                .setValue(threadId)
                .build();
    }

    private void releaseShard(ShardSessionRecord session) {
        var worker = session.getWorker();
        var shard = session.getIndex();
        client.get()
              .releaseShard(shard, worker);
    }

    @Override
    public Iterable<ShardIndex> releaseExpiredSessions(Duration inactivityPeriod) {
        checkNotDefaultArg(inactivityPeriod);
        var sessionsReleased =
                client.get()
                      .releaseExpiredSessions(inactivityPeriod);
        return sessionsReleased
                .getShardList()
                .stream()
                .map(ExpiredSession::getShard)
                .collect(ImmutableList.toImmutableList());
    }
}
