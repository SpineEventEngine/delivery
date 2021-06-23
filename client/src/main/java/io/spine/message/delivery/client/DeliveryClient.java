/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.spine.base.CommandMessage;
import io.spine.base.EventMessage;
import io.spine.client.Client;
import io.spine.logging.Logging;
import io.spine.message.delivery.command.PickUpShard;
import io.spine.message.delivery.command.ReleaseShard;
import io.spine.message.delivery.command.WriteMessage;
import io.spine.message.delivery.event.MessageWritten;
import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.message.delivery.event.ShardReleased;
import io.spine.server.NodeId;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.ShardIndex;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static io.spine.util.Preconditions2.checkNotDefaultArg;
import static io.spine.util.Preconditions2.checkNotEmptyOrBlank;
import static io.spine.util.Preconditions2.checkPositive;

final class DeliveryClient implements SessionRegistryClient, InboxClient, Logging {

    private final Client client;

    private DeliveryClient(ManagedChannel channel) {
        client = Client
                .usingChannel(channel)
                .withGuestId("DeliveryClient")
                .build();
    }

    /**
     * Creates a new delivery client which connects to a local gRPC server on port {@code 8484}.
     */
    static DeliveryClient local() {
        return create("127.0.0.1", 8484);
    }

    /**
     * Creates a new delivery client which connects to a gRPC server on the specified {@code host}
     * and {@code port}.
     */
    static DeliveryClient create(String host, int port) {
        checkNotEmptyOrBlank(host);
        checkPositive(port);
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();
        return new DeliveryClient(channel);
    }

    @Override
    public Optional<MessageWritten> writeMessage(InboxMessage message) {
        checkNotDefaultArg(message);
        var writeMessage = WriteMessage.newBuilder()
                .setMessage(message)
                .vBuild();
        var result = postCommand(writeMessage, MessageWritten.class);
        return result;
    }

    @Override
    public Optional<ShardPickedUp> pickUpShard(ShardIndex shard, NodeId worker) {
        checkNotDefaultArg(shard);
        checkNotDefaultArg(worker);
        var pickUpShard = PickUpShard.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .vBuild();
        var result = postCommand(pickUpShard, ShardPickedUp.class);
        return result;
    }

    @Override
    public Optional<ShardReleased> releaseShard(ShardIndex shard, NodeId worker) {
        checkNotDefaultArg(shard);
        checkNotDefaultArg(worker);
        var releaseShard = ReleaseShard.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .vBuild();
        var result = postCommand(releaseShard, ShardReleased.class);
        return result;
    }

    private <C extends CommandMessage, E extends EventMessage> Optional<E>
    postCommand(C command, Class<E> event) {
        _trace().log("Posting command `%s` and waiting for a response event `%s`.",
                     command.getClass(), event);
        var future = new CompletableFuture<Optional<E>>();
        var subscriptions =
                client.asGuest()
                      .command(command)
                      .observe(event, e -> {
                          _trace().log(
                                  "Received an event `%s` in response for a command `%s`.",
                                  event, command.getClass()
                          );
                          future.complete(Optional.of(e));
                      })
                      .onServerError((msg, error) -> {
                          _trace().log(
                                  "Server was not able to handle command `%s`: %s",
                                  command.getClass(), error
                          );
                          future.complete(Optional.empty());
                      })
                      .onStreamingError(future::completeExceptionally)
                      .post();
        var result = future.join();
        subscriptions.forEach(client.subscriptions()::cancel);
        return result;
    }
}
