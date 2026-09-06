/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.spine.delivery.server;

import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.spine.base.Identifier;
import io.spine.delivery.command.PickUpShard;
import io.spine.delivery.command.WriteMessage;
import io.spine.delivery.InboxServiceGrpc;
import io.spine.delivery.ShardServiceGrpc;
import io.spine.delivery.given.TestInboxMessages;
import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.DeliveryStrategy;
import io.spine.server.delivery.WorkerId;
import io.spine.test.delivery.Something;
import io.spine.type.TypeUrl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Isolated
@DisplayName("`DeliveryServerApp` should")
final class DeliveryServerAppTest extends WithApp {

    @Nested
    @DisplayName("expose")
    class Expose {

        @Test
        @DisplayName("`ShardService`")
        void shardService() {
            var node = ServerEnvironment.instance()
                    .nodeId();
            var worker = WorkerId.newBuilder()
                    .setNodeId(node)
                    .setValue(Identifier.newUuid())
                    .build();
            var shard = DeliveryStrategy.newIndex(0, 1);
            var pickUpShard = PickUpShard.newBuilder()
                    .setShard(shard)
                    .setWorker(worker)
                    .build();
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
                    .build();
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
