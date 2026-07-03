/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.server;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import io.spine.base.Time;
import io.spine.client.Client;
import io.spine.core.CommandContext;
import io.spine.delivery.SessionsCleaner;
import io.spine.delivery.SessionsCleanerId;
import io.spine.delivery.ShardSessionRegistry;
import io.spine.delivery.command.ReleaseExpiredSessions;
import io.spine.delivery.command.ReleaseShard;
import io.spine.delivery.event.ExpiredSession;
import io.spine.delivery.event.ExpiredSessionsReleased;
import io.spine.server.command.Assign;
import io.spine.server.procman.ProcessManager;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Releases sessions picked up prior to the configured inactivity period.
 *
 * <p>This process is intended to be used as a last resort functionality to remove locks
 * from stale session registries.
 */
final class SessionsCleanerProcess
        extends ProcessManager<SessionsCleanerId, SessionsCleaner, SessionsCleaner.Builder> {

    /**
     * A static date used for the sessions {@code whenPicked} filtering.
     *
     * <p>January 1 2021, 00:00:00 UTC.
     */
    private static final Timestamp YEAR_2021 = Timestamps.fromMillis(1609459200000L);

    private @MonotonicNonNull Client client;

    @Assign
    ExpiredSessionsReleased handle(ReleaseExpiredSessions c, CommandContext context) {
        var result = ExpiredSessionsReleased.newBuilder();
        var inactivityPeriod = c.getInactivityPeriod();
        var whenPickedPeriod = Timestamps.subtract(Time.currentTime(), inactivityPeriod);
        _info().log(
                "Querying shard session registries picked earlier than `%s`.", whenPickedPeriod
        );
        ImmutableList<ShardSessionRegistry> expiredSessions =
                client.asGuest()
                      .run(ShardSessionRegistry
                                   .query()
                                   .whenPicked()
                                   .isLessThan(whenPickedPeriod)
                                   .whenPicked()
                                   .isGreaterThan(YEAR_2021)
                                   .build()
                      );
        _info().log(
                "Releasing `%d` shard sessions.", expiredSessions.size()
        );
        for (ShardSessionRegistry expiredSession : expiredSessions) {
            releaseShard(expiredSession);
            result.addShard(fromRegistry(expiredSession));
        }
        return result.build();
    }

    private static ExpiredSession fromRegistry(ShardSessionRegistry expiredShardRegistry) {
        return ExpiredSession.newBuilder()
                .setShard(expiredShardRegistry.getId())
                .setWorker(expiredShardRegistry.getWorker())
                .setWhenPicked(expiredShardRegistry.getWhenPicked())
                .setWhenReleased(Time.currentTime())
                .build();
    }

    private void releaseShard(ShardSessionRegistry expiredShardRegistry) {
        ReleaseShard releaseShard = ReleaseShard.newBuilder()
                .setShard(expiredShardRegistry.getId())
                .setWorker(expiredShardRegistry.getWorker())
                .build();
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
    void setClient(Client client) {
        this.client = checkNotNull(client);
    }
}
