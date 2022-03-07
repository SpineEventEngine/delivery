/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.grpc;

import com.google.common.util.concurrent.Uninterruptibles;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.spine.base.Identifier;
import io.spine.message.delivery.command.PickUpShard;
import io.spine.message.delivery.command.ReleaseExpiredSessions;
import io.spine.message.delivery.command.ReleaseShard;
import io.spine.message.delivery.event.ExpiredSession;
import io.spine.message.delivery.event.ExpiredSessionsReleased;
import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.message.delivery.server.WithApp;
import io.spine.protobuf.Durations2;
import io.spine.server.NodeId;
import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.DeliveryStrategy;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.WorkerId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("`ShardService` should")
final class ShardServiceTest {

    private static final NodeId node = ServerEnvironment.instance()
            .nodeId();
    private static final WorkerId worker = WorkerId.newBuilder()
            .setNodeId(node)
            .setValue(Identifier.newUuid())
            .vBuild();
    private static final ShardIndex shard = DeliveryStrategy.newIndex(0, 1);
    private static final PickUpShard pickUpShard = PickUpShard.newBuilder()
            .setShard(shard)
            .setWorker(worker)
            .vBuild();

    @Nested
    @DisplayName("process `PickUpShard` request")
    final class Pick extends WithApp {

        @Test
        @DisplayName("picking up available shard")
        void pick() {
            var expected = ShardPickedUp.newBuilder()
                    .setShard(shard)
                    .setWorker(worker)
                    .buildPartial();
            var pickedUp = syncShardService().pickShard(pickUpShard);
            assertThat(pickedUp)
                    .comparingExpectedFieldsOnly()
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("not picking up already picked up shard")
        void notPickSame() {
            var shardService = syncShardService();
            assertDoesNotThrow(() -> {
                shardService.pickShard(pickUpShard);
            });
            var exception = assertThrows(
                    StatusRuntimeException.class, () -> shardService.pickShard(pickUpShard)
            );
            var status = exception.getStatus();
            assertThat(status.getCode())
                    .isEqualTo(Status.FAILED_PRECONDITION.getCode());
            assertThat(status.getDescription())
                    .isEqualTo("The shard has been already picked up.");
        }
    }

    @Nested
    @DisplayName("process `ReleaseShard` request")
    final class Release extends WithApp {

        private final ReleaseShard releaseShard = ReleaseShard.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .vBuild();

        @Test
        @DisplayName("doing nothing when shard is not picked up")
        void doNothing() {
            assertDoesNotThrow(() -> syncShardService().releaseSession(releaseShard));
        }

        @Test
        @DisplayName("releasing picked up shard")
        void releasePickedUp() {
            var shardService = syncShardService();
            assertDoesNotThrow(() -> {
                shardService.pickShard(pickUpShard);
                shardService.releaseSession(releaseShard);
                shardService.pickShard(pickUpShard);
            });
        }
    }

    @Nested
    @DisplayName("process `ReleaseExpiredSessions` request")
    final class ReleaseExpired extends WithApp {

        private final ReleaseExpiredSessions request = ReleaseExpiredSessions.newBuilder()
                .setInactivityPeriod(Durations2.seconds(2))
                .vBuild();

        @Test
        @DisplayName("doing nothing when no shards are picked up")
        void doNothing() {
            var response = syncShardService().releaseSessions(request);
            assertThat(response)
                    .isEqualToDefaultInstance();
        }

        @Test
        @DisplayName("release shards picked up earlier than supplied inactivity period")
        void releaseExpired() {
            var shardService = syncShardService();
            var shardPickedUp = shardService.pickShard(pickUpShard);
            Uninterruptibles.sleepUninterruptibly(3, TimeUnit.SECONDS);
            var expected = ExpiredSessionsReleased.newBuilder()
                    .addShard(ExpiredSession.newBuilder()
                                      .setShard(shardPickedUp.getShard())
                                      .setWorker(shardPickedUp.getWorker()))
                    .buildPartial();
            var response = shardService.releaseSessions(request);
            assertThat(response)
                    .comparingExpectedFieldsOnly()
                    .isEqualTo(expected);
        }
    }
}
