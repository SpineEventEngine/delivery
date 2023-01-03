/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.spine.base.Identifier;
import io.spine.client.Client;
import io.spine.environment.Environment;
import io.spine.message.delivery.InboxMessageHolder;
import io.spine.message.delivery.command.PickUpShard;
import io.spine.message.delivery.command.WriteMessage;
import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.DeliveryStrategy;
import io.spine.server.delivery.WorkerId;
import io.spine.test.message.delivery.server.Something;
import io.spine.type.TypeUrl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static io.spine.message.delivery.server.given.TestInboxMessages.toDeliver;

@Isolated
@DisplayName("`App` should")
final class AppTest {

    private final App app = new App();

    @AfterAll
    static void resetEnvs() {
        Environment.instance()
                .reset();
        ServerEnvironment.instance()
                .reset();
    }

    @BeforeEach
    void startApp() {
        var appThread = new Thread(app::initAndStart);
        appThread.start();
        sleepUninterruptibly(Duration.ofSeconds(3)); // allow the server to start.
    }

    @AfterEach
    void shutdownApp() {
        app.shutdown();
    }

    @Test
    @DisplayName("expose gRPC client")
    void exposeClient() {
        var client = newClient();
        assertThat(client.isOpen())
                .isTrue();
    }

    @Test
    @DisplayName("handle commands")
    void handleCommands() throws InterruptedException {
        var node = ServerEnvironment.instance()
                .nodeId();
        var worker = WorkerId.newBuilder()
                .setNodeId(node)
                .setValue(Identifier.newUuid())
                .vBuild();
        var shard = DeliveryStrategy.newIndex(0, 1);
        var pickUpShard = PickUpShard.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .vBuild();
        var expectedEvent = ShardPickedUp.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .buildPartial();
        var client = newClient();
        CountDownLatch shardPickedUp = new CountDownLatch(1);
        client.asGuest()
              .command(pickUpShard)
              .observe(ShardPickedUp.class, event -> {
                  assertThat(event)
                          .comparingExpectedFieldsOnly()
                          .isEqualTo(expectedEvent);
                  shardPickedUp.countDown();
              })
              .post();
        shardPickedUp.await(3, TimeUnit.SECONDS);
        client.shutdown();
    }

    @Test
    @DisplayName("query projections")
    void queryProjection() throws InterruptedException {
        var message = toDeliver(Identifier.newUuid(), TypeUrl.from(Something.getDescriptor()));
        var writeMessage = WriteMessage.newBuilder()
                .setMessage(message)
                .vBuild();
        var client = newClient();
        CountDownLatch messageWritten = new CountDownLatch(1);
        client.asGuest()
              .command(writeMessage)
              .postAndForget();
        var expected = InboxMessageHolder.newBuilder()
                .setId(message.getId())
                .setMessage(message)
                .setMessage(message)
                .setShard(message.shardIndex())
                .setInbox(message.getInboxId())
                .setSignal(message.getSignalId())
                .setIsEvent(message.hasEvent())
                .setIsCommand(message.hasCommand())
                .setLabel(message.getLabel())
                .setStatus(message.getStatus())
                .setReceivedAt(message.getWhenReceived())
                .setVersion(message.getVersion())
                .vBuild();
        client.asGuest()
              .subscribeTo(InboxMessageHolder.class)
              .byId(message.getId())
              .observe(inboxMessageHolder -> {
                  assertThat(inboxMessageHolder)
                          .isEqualTo(expected);
                  messageWritten.countDown();
              })
              .post();
        messageWritten.await(10, TimeUnit.SECONDS);
        client.shutdown();
    }

    private static Client newClient() {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(App.HOST, App.PORT)
                .usePlaintext()
                .build();
        return Client
                .usingChannel(channel)
                .build();
    }
}
