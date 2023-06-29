/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import com.google.protobuf.Timestamp;
import io.spine.base.Identifier;
import io.spine.base.Time;
import io.spine.message.delivery.CurrentShardState;
import io.spine.message.delivery.grpc.command.PickUpShard;
import io.spine.message.delivery.grpc.command.ReleaseShard;
import io.spine.server.NodeId;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.WorkerId;
import io.spine.time.testing.FrozenMadHatterParty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("`SessionHolder` should")
@SuppressWarnings("resource") // `context()` should not be closed in test methods.
class CurrentShardStateProjectionTest extends DeliveryTest {

    private static final ShardIndex shard = ShardIndex.newBuilder()
            .setIndex(0)
            .setOfTotal(2)
            .vBuild();
    private static final NodeId node = NodeId.newBuilder()
            .setValue(Identifier.newUuid())
            .vBuild();
    private static final WorkerId worker = WorkerId.newBuilder()
            .setNodeId(node)
            .setValue(Identifier.newUuid())
            .vBuild();

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
                .vBuild();

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
                .vBuild();

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
                .vBuild();
    }

    /**
     * Creates new {@code ReleaseShard} command with predefined parameters.
     */
    private static ReleaseShard releaseShard() {
        return ReleaseShard
                .newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .vBuild();
    }
}
