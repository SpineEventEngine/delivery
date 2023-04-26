/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.grpc;

import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Durations;
import io.spine.base.Time;
import io.spine.message.delivery.grpc.LiquorPickUpOutcome;
import io.spine.message.delivery.rejection.Rejections;
import io.spine.message.delivery.server.WithApp;
import io.spine.message.delivery.server.grpc.given.ShardServiceTestEnv;
import io.spine.time.testing.FrozenMadHatterParty;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static io.spine.message.delivery.server.grpc.given.ShardServiceTestEnv.asPickedUp;
import static io.spine.message.delivery.server.grpc.given.ShardServiceTestEnv.asReleased;
import static io.spine.message.delivery.server.grpc.given.ShardServiceTestEnv.pickUpShard;
import static io.spine.message.delivery.server.grpc.given.ShardServiceTestEnv.release;
import static io.spine.message.delivery.server.grpc.given.ShardServiceTestEnv.releaseExpiredSessions;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DisplayName("`ShardService` should")
final class ShardServiceTest {

    private static final ShardServiceTestEnv env = new ShardServiceTestEnv();

    @AfterAll
    static void releaseResources() {
        env.close();
    }

    @Nested
    @DisplayName("process `PickUpShard` request")
    final class Pick extends WithApp {

        @Test
        @DisplayName("picking up available shard")
        void pickAvailable() {
            var request = pickUpShard();
            var pickedUp = syncShardService().pickShard(request);
            var expected = asPickedUp(request);
            assertThat(pickedUp.hasPickedUp())
                    .isTrue();
            assertThat(pickedUp.getPickedUp())
                    .comparingExpectedFieldsOnly()
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("picking up stale shard")
        void pickStale() {
            var precessingTimeout = Durations.fromSeconds(3);
            var shardService = env.syncShardService(precessingTimeout);
            var request = pickUpShard();
            var firstlyPickedUp = shardService.pickShard(request);
            sleepUninterruptibly(precessingTimeout.getSeconds() + 1, TimeUnit.SECONDS);
            var secondlyPickedUp = shardService.pickShard(request);
            var expected = asPickedUp(request);
            assertThat(secondlyPickedUp.hasPickedUp())
                    .isTrue();
            assertThat(secondlyPickedUp.getPickedUp())
                    .comparingExpectedFieldsOnly()
                    .isEqualTo(expected);
            assertThat(firstlyPickedUp.hasPickedUp())
                    .isTrue();
            assertThat(firstlyPickedUp.getPickedUp()
                                      .getWhenPicked())
                    .isNotEqualTo(secondlyPickedUp.getPickedUp()
                                                  .getWhenPicked());
        }

        @Test
        @DisplayName("not picking up already picked up shard")
        void notPickSame() {
            Timestamp frozen = Time.currentTime();
            Time.setProvider(new FrozenMadHatterParty(frozen));
            var shardService = syncShardService();
            var request = pickUpShard();
            LiquorPickUpOutcome firstAttempt = shardService.pickShard(request);
            assertThat(firstAttempt.hasPickedUp())
                    .isTrue();
            LiquorPickUpOutcome secondAttempt = shardService.pickShard(request);
            assertThat(secondAttempt.hasAlreadyPickedUp())
                    .isTrue();

            Rejections.ShardAlreadyPickedUp expected = Rejections.ShardAlreadyPickedUp
                    .newBuilder()
                    .setShard(request.getShard())
                    .setWorker(request.getWorker())
                    .setWhenPicked(frozen)
                    .vBuild();

            assertThat(secondAttempt.getAlreadyPickedUp())
                    .comparingExpectedFieldsOnly()
                    .isEqualTo(expected);
            Time.resetProvider();
        }
    }

    @Nested
    @DisplayName("process `ReleaseShard` request")
    final class Release extends WithApp {

        @Test
        @DisplayName("doing nothing when shard is not picked up")
        void doNothing() {
            var pickUpRequest = pickUpShard(); // Will not be executed intentionally.
            var releaseRequest = release(pickUpRequest);
            assertDoesNotThrow(() -> syncShardService().releaseSession(releaseRequest));
        }

        @Test
        @DisplayName("releasing picked up shard")
        void releasePickedUp() {
            var shardService = syncShardService();
            var pickUpRequest = pickUpShard();
            var releaseRequest = release(pickUpRequest);
            assertDoesNotThrow(() -> {
                shardService.pickShard(pickUpRequest);
                shardService.releaseSession(releaseRequest);
                shardService.pickShard(pickUpRequest);
            });
        }
    }

    @Nested
    @DisplayName("process `ReleaseExpiredSessions` request")
    final class ReleaseExpired extends WithApp {

        @Test
        @DisplayName("doing nothing when no shards are picked up")
        void doNothing() {
            var inactivityPeriod = Durations.fromSeconds(2);
            var request = releaseExpiredSessions(inactivityPeriod);
            var response = syncShardService().releaseSessions(request);
            assertThat(response).isEqualToDefaultInstance();
        }

        @Test
        @DisplayName("release shards picked up earlier than supplied inactivity period")
        void releaseExpired() {
            var shardService = syncShardService();
            var pickUpRequest = pickUpShard();
            var pickedUp = shardService.pickShard(pickUpRequest);
            var inactivityPeriod = Durations.fromSeconds(2);
            sleepUninterruptibly(inactivityPeriod.getSeconds() + 1, TimeUnit.SECONDS);
            var request = releaseExpiredSessions(inactivityPeriod);
            var response = shardService.releaseSessions(request);
            var expected = asReleased(pickedUp.getPickedUp());
            assertThat(response)
                    .comparingExpectedFieldsOnly()
                    .isEqualTo(expected);
        }
    }
}
