/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import com.google.protobuf.Timestamp;
import io.spine.base.Identifier;
import io.spine.base.Time;
import io.spine.message.delivery.server.command.PickUpShard;
import io.spine.message.delivery.server.event.ShardPickedUp;
import io.spine.message.delivery.server.rejection.Rejections;
import io.spine.server.NodeId;
import io.spine.server.delivery.ShardIndex;
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
    private final NodeId worker = NodeId.newBuilder()
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
                    .setPickedBy(worker)
                    .setId(shard)
                    .setWhenPicked(time)
                    .vBuild();
            context().assertState(shard, expected);
        }

        @Test
        @DisplayName("producing `ShardPickedUp` event")
        void event() {
            var expected = ShardPickedUp.newBuilder()
                    .setShard(shard)
                    .setWhenPicked(time)
                    .setPickedBy(worker)
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
}
