/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.util.Timestamps;
import io.spine.base.Time;
import io.spine.client.Client;
import io.spine.core.CommandContext;
import io.spine.logging.Logging;
import io.spine.message.delivery.SessionsCleaner;
import io.spine.message.delivery.SessionsCleanerId;
import io.spine.message.delivery.ShardSessionRegistry;
import io.spine.message.delivery.command.ReleaseExpiredSessions;
import io.spine.message.delivery.command.ReleaseShard;
import io.spine.message.delivery.event.ExpiredSession;
import io.spine.message.delivery.event.ExpiredSessionsReleased;
import io.spine.server.command.Assign;
import io.spine.server.procman.ProcessManager;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import static com.google.common.base.Preconditions.checkNotNull;

public class SessionsCleanerProcess
        extends ProcessManager<SessionsCleanerId, SessionsCleaner, SessionsCleaner.Builder>
        implements Logging {

    private @MonotonicNonNull Client client;

    @Assign
    ExpiredSessionsReleased handle(ReleaseExpiredSessions c, CommandContext context) {
        var result = ExpiredSessionsReleased.newBuilder();
        var inactivityPeriod = c.getInactivityPeriod();
        var whenPickedPeriod = Timestamps.subtract(Time.currentTime(), inactivityPeriod);
        ImmutableList<ShardSessionRegistry> expiredShards =
                client.asGuest()
                      .run(ShardSessionRegistry
                                   .query()
                                   .whenPicked()
                                   .isLessThan(whenPickedPeriod)
                                   .build()
                      );
        for (ShardSessionRegistry expiredShardRegistry : expiredShards) {
            releaseShard(expiredShardRegistry);
            result.addShard(fromRegistry(expiredShardRegistry));
        }
        return result.vBuild();
    }

    private static ExpiredSession fromRegistry(ShardSessionRegistry expiredShardRegistry) {
        return ExpiredSession.newBuilder()
                .setShard(expiredShardRegistry.getId())
                .setPickedBy(expiredShardRegistry.getPickedBy())
                .setWhenPicked(expiredShardRegistry.getWhenPicked())
                .setWhenReleased(Time.currentTime())
                .vBuild();
    }

    private void releaseShard(ShardSessionRegistry expiredShardRegistry) {
        ReleaseShard releaseShard = ReleaseShard.newBuilder()
                .setShard(expiredShardRegistry.getId())
                .setWorker(expiredShardRegistry.getPickedBy())
                .vBuild();
        client.asGuest()
              .command(releaseShard)
              .postAndForget();
    }

    /**
     * Sets the client to be used by this process to query the context entities.
     *
     * @apiNote This method is intended to be used as part of the entity configuration
     *         done through the repository.
     */
    public void setClient(Client client) {
        this.client = checkNotNull(client);
    }
}
