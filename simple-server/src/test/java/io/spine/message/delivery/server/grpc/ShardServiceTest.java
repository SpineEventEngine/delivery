/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.spine.message.delivery.command.PickUpShard;
import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.message.delivery.grpc.ShardServiceGrpc;
import io.spine.message.delivery.server.WithApp;
import io.spine.server.NodeId;
import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.DeliveryStrategy;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.storage.memory.InMemoryStorageFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("`ShardService` should")
final class ShardServiceTest {

    @Nested
    @DisplayName("process `pickShard` request")
    final class PickShard extends WithApp {

        private final NodeId worker = ServerEnvironment.instance()
                .nodeId();
        private final ShardIndex shard = DeliveryStrategy.newIndex(0, 1);
        private final PickUpShard pickUpShard = PickUpShard.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .vBuild();

        @Test
        @DisplayName("picking up available shard")
        void pick() {
            var shardService = ShardServiceGrpc.newBlockingStub(serverChannel());

            var expected = ShardPickedUp.newBuilder()
                    .setShard(shard)
                    .setPickedBy(worker)
                    .buildPartial();
            var pickedUp = shardService.pickShard(pickUpShard);
            assertThat(pickedUp)
                    .comparingExpectedFieldsOnly()
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("not picking up already picked up shard")
        void notPickSame() {
            var shardService = ShardServiceGrpc.newBlockingStub(serverChannel());
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
}
