/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.server;

import com.google.protobuf.Timestamp;
import io.spine.base.Identifier;
import io.spine.base.Time;
import io.spine.delivery.CurrentShardState;
import io.spine.delivery.command.PickUpShard;
import io.spine.delivery.command.ReleaseShard;
import io.spine.server.NodeId;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.WorkerId;
import io.spine.testing.time.FrozenMadHatterParty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("`SessionHolder` should")
@SuppressWarnings("resource") // `context()` should not be closed in test methods.
class CurrentShardStateProjectionTest extends DeliveryTest {

    private static final ShardIndex shard = ShardIndex.newBuilder()
            .setIndex(0)
            .setOfTotal(2)
            .build();
    private static final NodeId node = NodeId.newBuilder()
            .setValue(Identifier.newUuid())
            .build();
    private static final WorkerId worker = WorkerId.newBuilder()
            .setNodeId(node)
            .setValue(Identifier.newUuid())
            .build();

    @Test
    @DisplayName("subscribe to `ShardPickedUp` event")
    void subscribeToPickedUp() {
        Timestamp time = Time.currentTime();
        Time.setProvider(new FrozenMadHatterParty(time));
        context().receivesCommand(pickUpShard());

        CurrentShardState expected = CurrentShardState
                .newBuilder()
                .setId(shard)
                .setWorker(worker)
                .setWhenLastPicked(time)
                .build();

        context().assertState(shard, expected);
    }

    @Test
    @DisplayName("subscribe to `ShardReleased` event")
    void subscribeToReleased() {
        Timestamp time = Time.currentTime();
        Time.setProvider(new FrozenMadHatterParty(time));

        context().receivesCommands(pickUpShard(), releaseShard());

        CurrentShardState expected = CurrentShardState
                .newBuilder()
                .setId(shard)
                .setWhenLastPicked(time)
                .build();

        context().assertEntity(shard, CurrentShardStateProjection.class)
                 .hasStateThat()
                 .isEqualTo(expected);
    }

    @AfterEach
    void restoreTime() {
        Time.resetProvider();
    }

    /**
     * Creates new {@code PickUpShard} command with predefined parameters.
     */
    private static PickUpShard pickUpShard() {
        return PickUpShard.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .build();
    }

    /**
     * Creates new {@code ReleaseShard} command with predefined parameters.
     */
    private static ReleaseShard releaseShard() {
        return ReleaseShard
                .newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .build();
    }
}
