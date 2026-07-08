/*
 * Copyright 2026, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.delivery.server;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.spine.base.Identifier;
import io.spine.client.Client;
import io.spine.delivery.InboxMessageHolder;
import io.spine.delivery.command.PickUpShard;
import io.spine.delivery.command.WriteMessage;
import io.spine.delivery.event.ShardPickedUp;
import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.DeliveryStrategy;
import io.spine.server.delivery.WorkerId;
import io.spine.test.delivery.server.Something;
import io.spine.type.TypeUrl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static io.spine.delivery.server.given.TestInboxMessages.toDeliver;

@Isolated
@DisplayName("`App` should")
final class AppTest extends WithApp {

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
                .build();
        var shard = DeliveryStrategy.newIndex(0, 1);
        var pickUpShard = PickUpShard.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .build();
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
                .build();
        var client = newClient();
        CountDownLatch messageWritten = new CountDownLatch(1);
        client.asGuest()
              .command(writeMessage)
              .postAndForget();
        var expected = InboxMessageHolder.newBuilder()
                .setId(message.getId())
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
                .build();
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

    private Client newClient() {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(App.HOST, port())
                .usePlaintext()
                .build();
        return Client
                .usingChannel(channel)
                .build();
    }
}
