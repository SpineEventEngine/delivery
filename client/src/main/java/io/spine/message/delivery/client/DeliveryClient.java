/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import com.google.common.collect.ImmutableSet;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.spine.base.CommandMessage;
import io.spine.base.EventMessage;
import io.spine.client.Client;
import io.spine.client.Subscription;
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
    @SuppressWarnings("CanIgnore")
    static DeliveryClient create(String host, int port) {
        checkNotEmptyOrBlank(host);
//        checkPositive(port);
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();
        return new DeliveryClient(channel);
    }

    /**
     * Creates a new delivery client which connects to a gRPC server on the specified
     * {@code target}.
     *
     * <p>It is assumed that the target is using a secure connection.
     */
    static DeliveryClient create(String target) {
        checkNotEmptyOrBlank(target);
        ManagedChannel channel = ManagedChannelBuilder
                .forTarget(target)
                .build();
        return new DeliveryClient(channel);
    }

    @Override
    public Optional<MessageWritten> writeMessage(InboxMessage message) {
        checkNotDefaultArg(message);
        WriteMessage writeMessage = WriteMessage.newBuilder()
                .setMessage(message)
                .vBuild();
        Optional<MessageWritten> result = postCommand(writeMessage, MessageWritten.class);
        return result;
    }

    @Override
    public Optional<ShardPickedUp> pickUpShard(ShardIndex shard, NodeId worker) {
        checkNotDefaultArg(shard);
        checkNotDefaultArg(worker);
        PickUpShard pickUpShard = PickUpShard.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .vBuild();
        Optional<ShardPickedUp> result = postCommand(pickUpShard, ShardPickedUp.class);
        return result;
    }

    @Override
    public Optional<ShardReleased> releaseShard(ShardIndex shard, NodeId worker) {
        checkNotDefaultArg(shard);
        checkNotDefaultArg(worker);
        ReleaseShard releaseShard = ReleaseShard.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .vBuild();
        Optional<ShardReleased> result = postCommand(releaseShard, ShardReleased.class);
        return result;
    }

    private <C extends CommandMessage, E extends EventMessage> Optional<E>
    postCommand(C command, Class<E> event) {
        _trace().log("Posting command `%s` and waiting for a response event `%s`.",
                     command.getClass(), event);
        CompletableFuture<Optional<E>> future = new CompletableFuture<Optional<E>>();
        ImmutableSet<Subscription> subscriptions =
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
        Optional<E> result = future.join();
        subscriptions.forEach(client.subscriptions()::cancel);
        return result;
    }
}
