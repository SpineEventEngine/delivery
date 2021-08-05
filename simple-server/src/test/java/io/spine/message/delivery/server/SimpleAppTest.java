/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.time.Duration;

import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Isolated
@DisplayName("`SimpleApp` should")
final class SimpleAppTest {

    private final SimpleApp app = new SimpleApp();

    @BeforeEach
    void startApp() {
        var appThread = new Thread(app::initAndStart);
        appThread.start();
        sleepUninterruptibly(Duration.ofSeconds(1)); // allow the server to start.
    }

    @AfterEach
    void shutdownApp() {
        app.shutdown();
    }

    @Nested
    @DisplayName("expose")
    class Expose {

        private final ManagedChannel channel = serverChannel();

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
            var shardService = ShardServiceGrpc.newBlockingStub(channel);
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
            var inboxService = InboxServiceGrpc.newFutureStub(channel);
            assertDoesNotThrow(() -> {
                inboxService.writeOne(writeMessage);
            });
        }
    }

    private static ManagedChannel serverChannel() {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(SimpleApp.HOST, SimpleApp.PORT)
                .usePlaintext()
                .build();
        return channel;
    }
}
