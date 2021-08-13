/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.spine.base.Identifier;
import io.spine.message.delivery.command.PickUpShard;
import io.spine.message.delivery.command.WriteMessage;
import io.spine.message.delivery.grpc.InboxServiceGrpc;
import io.spine.message.delivery.grpc.ShardServiceGrpc;
import io.spine.message.delivery.server.given.TestInboxMessages;
import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.DeliveryStrategy;
import io.spine.test.message.delivery.server.Something;
import io.spine.type.TypeUrl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Isolated
@DisplayName("`SimpleApp` should")
final class SimpleAppTest extends WithApp {

    @Nested
    @DisplayName("expose")
    class Expose {

        @Test
        @DisplayName("`ShardService`")
        void shardService() {
            var worker = ServerEnvironment.instance()
                    .nodeId();
            var shard = DeliveryStrategy.newIndex(0, 1);
            var pickUpShard = PickUpShard.newBuilder()
                    .setShard(shard)
                    .setWorker(worker)
                    .vBuild();
            var shardService = ShardServiceGrpc.newBlockingStub(serverChannel());
            assertDoesNotThrow(() -> {
                shardService.pickShard(pickUpShard);
            });
        }

        @Test
        @DisplayName("`InboxService`")
        void inboxService() {
            var message = TestInboxMessages
                    .toDeliver(Identifier.newUuid(), TypeUrl.of(Something.class));
            var writeMessage = WriteMessage.newBuilder()
                    .setMessage(message)
                    .vBuild();
            var inboxService = InboxServiceGrpc.newFutureStub(serverChannel());
            assertDoesNotThrow(() -> {
                inboxService.writeOne(writeMessage);
            });
        }

        @Test
        @DisplayName("`HealthService`")
        void healthService() {
            var expected = HealthCheckResponse.newBuilder()
                    .setStatus(HealthCheckResponse.ServingStatus.SERVING)
                    .buildPartial();
            var message = HealthCheckRequest.newBuilder()
                    .buildPartial();
            var service = HealthGrpc.newBlockingStub(serverChannel());
            var response = service.check(message);
            assertThat(response)
                    .isEqualTo(expected);
        }
    }
}
