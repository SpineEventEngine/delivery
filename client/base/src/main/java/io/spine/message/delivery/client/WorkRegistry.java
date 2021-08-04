/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Duration;
import io.spine.logging.Logging;
import io.spine.message.delivery.event.ExpiredSession;
import io.spine.message.delivery.event.ExpiredSessionsReleased;
import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.server.NodeId;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.ShardProcessingSession;
import io.spine.server.delivery.ShardSessionRecord;
import io.spine.server.delivery.ShardedWorkRegistry;

import java.util.Optional;
import java.util.function.Consumer;
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

    @Override
    public Optional<ShardProcessingSession> pickUp(ShardIndex index, NodeId nodeId) {
        checkNotDefaultArg(index);
        checkNotDefaultArg(nodeId);
        return client
                .get()
                .pickUpShard(index, nodeId)
                .map(WorkRegistry::session)
                .map(sessionRecord -> new Session(sessionRecord, this::releaseShard));
    }

    private static ShardSessionRecord session(ShardPickedUp event) {
        return ShardSessionRecord.newBuilder()
                .setIndex(event.getShard())
                .setPickedBy(event.getPickedBy())
                .setWhenLastPicked(event.getWhenPicked())
                .vBuild();
    }

    private void releaseShard(ShardSessionRecord session) {
        NodeId worker = session.getPickedBy();
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

    private static final class Session extends ShardProcessingSession {

        private final Consumer<ShardSessionRecord> onComplete;
        private final ShardSessionRecord record;

        private Session(ShardSessionRecord record, Consumer<ShardSessionRecord> onComplete) {
            super(record);
            this.onComplete = onComplete;
            this.record = record;
        }

        @Override
        protected void complete() {
            onComplete.accept(record);
        }
    }
}
