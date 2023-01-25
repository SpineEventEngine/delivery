/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import com.google.protobuf.Timestamp;
import io.spine.base.Identifier;
import io.spine.base.Time;
import io.spine.message.delivery.ShardSessionRegistry;
import io.spine.message.delivery.command.PickUpShard;
import io.spine.message.delivery.command.ReleaseShard;
import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.message.delivery.event.ShardReleased;
import io.spine.message.delivery.rejection.Rejections;
import io.spine.server.NodeId;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.WorkerId;
import io.spine.time.testing.FrozenMadHatterParty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("`SessionRegistry` should")
final class SessionRegistryTest extends DeliveryTest {

    private final ShardIndex shard = ShardIndex.newBuilder()
            .setIndex(0)
            .setOfTotal(2)
            .vBuild();
    private final NodeId node = NodeId.newBuilder()
            .setValue(Identifier.newUuid())
            .vBuild();
    private final WorkerId worker = WorkerId.newBuilder()
            .setNodeId(node)
            .setValue(Identifier.newUuid())
            .vBuild();

    @Nested
    @DisplayName("handle `PickUpShard` command")
    final class HandleShardPickUp {

        private final Timestamp time = Time.currentTime();
        private final PickUpShard command = PickUpShard.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .vBuild();

        @BeforeEach
        void pickUpShard() {
            Time.setProvider(new FrozenMadHatterParty(time));
            context().receivesCommand(command);
        }

        @AfterEach
        void resetTime() {
            Time.resetProvider();
        }

        @Test
        @DisplayName("updating aggregate state")
        void state() {
            var expected = ShardSessionRegistry.newBuilder()
                    .setWorker(worker)
                    .setId(shard)
                    .setWhenPicked(time)
                    .setWhenLastPicked(time)
                    .vBuild();
            context().assertState(shard, expected);
        }

        @Test
        @DisplayName("producing `ShardPickedUp` event")
        void event() {
            var expected = ShardPickedUp.newBuilder()
                    .setShard(shard)
                    .setWhenPicked(time)
                    .setWorker(worker)
                    .vBuild();
            context().assertEvent(expected);
        }

        @Nested
        @DisplayName("throwing")
        class Throwing {

            @Test
            @DisplayName("`ShardAlreadyPickedUp` rejection when the shard is already picked up")
            void rejection() {
                context().receivesCommand(command);

                var expected = Rejections.ShardAlreadyPickedUp.newBuilder()
                        .setShard(shard)
                        .setWorker(worker)
                        .vBuild();
                context().assertEvent(expected);
            }
        }
    }

    @Nested
    @DisplayName("handle `ReleaseShard` command")
    final class HandleReleaseShard {

        private final Timestamp time = Time.currentTime();
        private final ReleaseShard releaseShard = ReleaseShard.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .vBuild();

        @BeforeEach
        void releaseShard() {
            Time.setProvider(new FrozenMadHatterParty(time));
            var pickUpShard = PickUpShard.newBuilder()
                    .setShard(shard)
                    .setWorker(worker)
                    .vBuild();
            context().receivesCommand(pickUpShard)
                     .receivesCommand(releaseShard);
        }

        @AfterEach
        void resetTime() {
            Time.resetProvider();
        }

        @Test
        @DisplayName("updating aggregate state")
        void state() {
            var expected = ShardSessionRegistry.newBuilder()
                    .setId(shard)
                    .vBuild();
            context().assertState(shard, expected);
        }

        @Test
        @DisplayName("producing `ShardReleased` event")
        void event() {
            var expected = ShardReleased.newBuilder()
                    .setShard(shard)
                    .setWhenReleased(time)
                    .setWorker(worker)
                    .vBuild();
            context().assertEvent(expected);
        }

        @Nested
        @DisplayName("throwing `UnableToReleaseShard` rejection when")
        class Throwing {

            @Test
            @DisplayName("shard was not previously picked up")
            void notPickedUp() {
                context().receivesCommand(releaseShard);
                var expected = Rejections.UnableToReleaseShard.newBuilder()
                        .setShard(shard)
                        .setWorker(worker)
                        .setReason(SessionRegistry.shardNotPickedUp())
                        .vBuild();
                context().assertEvent(expected);
            }

            @Test
            @DisplayName("shard is picked up by another worker")
            void pickedUpByAnotherWorker() {
                var anotherWorker = WorkerId.newBuilder()
                        .setNodeId(node)
                        .setValue(Identifier.newUuid())
                        .vBuild();
                var pickUpShard = PickUpShard.newBuilder()
                        .setShard(shard)
                        .setWorker(anotherWorker)
                        .vBuild();
                context().receivesCommand(pickUpShard)
                         .receivesCommand(releaseShard);
                var expected = Rejections.UnableToReleaseShard.newBuilder()
                        .setShard(shard)
                        .setWorker(worker)
                        .setReason(SessionRegistry.shardPickedUpByOtherWorker(anotherWorker))
                        .vBuild();
                context().assertEvent(expected);
            }
        }
    }
}
