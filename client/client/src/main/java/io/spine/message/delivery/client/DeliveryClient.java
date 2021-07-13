/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.spine.base.CommandMessage;
import io.spine.base.Error;
import io.spine.client.Client;
import io.spine.client.OrderBy;
import io.spine.client.QueryFilter;
import io.spine.logging.Logging;
import io.spine.message.delivery.InboxMessageHolder;
import io.spine.message.delivery.InboxMessageHolder.Column;
import io.spine.message.delivery.command.PickUpShard;
import io.spine.message.delivery.command.ReleaseShard;
import io.spine.message.delivery.command.RemoveMessage;
import io.spine.message.delivery.command.RemoveMessages;
import io.spine.message.delivery.command.WriteMessage;
import io.spine.message.delivery.command.WriteMessages;
import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.message.delivery.grpc.ShardSessionRegistryServiceGrpc;
import io.spine.message.delivery.grpc.ShardSessionRegistryServiceGrpc.ShardSessionRegistryServiceBlockingStub;
import io.spine.server.NodeId;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.InboxMessageComparator;
import io.spine.server.delivery.InboxMessageId;
import io.spine.server.delivery.Page;
import io.spine.server.delivery.ShardIndex;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Iterables.toArray;
import static io.spine.client.OrderBy.Direction.DESCENDING;
import static io.spine.client.QueryFilter.eq;
import static io.spine.client.QueryFilter.gt;
import static io.spine.server.delivery.InboxMessageStatus.TO_DELIVER;
import static io.spine.util.Preconditions2.checkNotDefaultArg;
import static io.spine.util.Preconditions2.checkNotEmptyOrBlank;
import static io.spine.util.Preconditions2.checkPositive;

/**
 * A client for working with the Message Delivery server.
 *
 * <p>Provides APIs for modifying and querying the remote state of the Message Delivery context.
 */
public final class DeliveryClient implements SessionRegistryClient, InboxClient, Logging {

    private final Client client;
    private final ShardSessionRegistryServiceBlockingStub sessionRegistry;

    private DeliveryClient(ManagedChannel channel) {
        client = Client
                .usingChannel(channel)
                .withGuestId("DeliveryClient")
                .build();
        sessionRegistry = ShardSessionRegistryServiceGrpc.newBlockingStub(channel);
    }

    /**
     * Creates a new delivery client which connects to a gRPC server on the specified {@code host}
     * and {@code port}.
     */
    @SuppressWarnings("CheckReturnValue" /* We're fine to just `check` args. */)
    static DeliveryClient create(String host, int port) {
        checkNotEmptyOrBlank(host);
        checkPositive(port);
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();
        return new DeliveryClient(channel);
    }

    /**
     * Creates a new delivery client which connects to a gRPC server
     * using specified {@code channel}.
     */
    public static DeliveryClient create(ManagedChannel channel) {
        checkNotNull(channel);
        return new DeliveryClient(channel);
    }

    @Override
    public void writeMessage(InboxMessage message) {
        checkNotDefaultArg(message);
        WriteMessage writeMessage = WriteMessage.newBuilder()
                .setMessage(message)
                .vBuild();
        post(writeMessage);
    }

    @Override
    public void writeMessages(ShardIndex shard, Iterable<InboxMessage> messages) {
        checkNotDefaultArg(shard);
        checkNotNull(messages);
        WriteMessages writeMessages = WriteMessages.newBuilder()
                .setShard(shard)
                .addAllMessage(messages)
                .vBuild();
        post(writeMessages);
    }

    @Override
    public void removeMessage(InboxMessage message) {
        checkNotDefaultArg(message);
        RemoveMessage removeMessage = RemoveMessage.newBuilder()
                .setMessage(message)
                .vBuild();
        post(removeMessage);
    }

    @Override
    public void removeMessages(ShardIndex shard, Iterable<InboxMessage> messages) {
        checkNotDefaultArg(shard);
        checkNotNull(messages);
        RemoveMessages removeMessages = RemoveMessages.newBuilder()
                .setShard(shard)
                .addAllMessage(messages)
                .vBuild();
        post(removeMessages);
    }

    @Override
    public Optional<ShardPickedUp> pickUpShard(ShardIndex shard, NodeId worker) {
        checkNotDefaultArg(shard);
        checkNotDefaultArg(worker);
        PickUpShard pickUpShard = PickUpShard.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .vBuild();
        _trace().log(
                "Posting `PickUpShard` command and waiting for a response event `ShardPickedUp`."
        );
        try {
            ShardPickedUp shardPickedUp = sessionRegistry.pickShard(pickUpShard);
            return Optional.of(shardPickedUp);
        } catch (RuntimeException e) {
            _info().log("Unable to pick up shard `%s`.", shard);
        }
        return Optional.empty();
    }

    @Override
    public void releaseShard(ShardIndex shard, NodeId worker) {
        checkNotDefaultArg(shard);
        checkNotDefaultArg(worker);
        ReleaseShard releaseShard = ReleaseShard.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .vBuild();
        post(releaseShard);
    }

    @Override
    public Optional<InboxMessage> find(InboxMessageId messageId) {
        checkNotDefaultArg(messageId);
        return client.asGuest()
                     .select(InboxMessageHolder.class)
                     .byId(messageId)
                     .run()
                     .stream()
                     .findFirst()
                     .map(InboxMessageHolder::getMessage);
    }

    @Override
    public Page<InboxMessage> readAll(ShardIndex shard, int pageSize) {
        Page<InboxMessage> page = new InboxPage(sinceWhen -> readAll(shard, sinceWhen, pageSize));
        return page;
    }

    private ImmutableList<InboxMessage>
    readAll(ShardIndex shard, @Nullable Timestamp sinceWhen, int pageSize) {
        ImmutableList.Builder<QueryFilter> filters = ImmutableList.<QueryFilter>builder()
                .add(eq(Column.shard(), shard));
        if (sinceWhen != null) {
            filters.add(gt(Column.receivedAt(), sinceWhen));
        }
        ImmutableList<InboxMessage> result =
                client.asGuest()
                      .select(InboxMessageHolder.class)
                      .where(toArray(filters.build(), QueryFilter.class))
                      .limit(pageSize)
                      .orderBy(Column.receivedAt(), OrderBy.Direction.ASCENDING)
                      .run()
                      .stream()
                      .map(InboxMessageHolder::getMessage)
                      .sorted(InboxMessageComparator.chronologically)
                      .collect(toImmutableList());
        return result;
    }

    @Override
    public Optional<InboxMessage> newestMessageToDeliver(ShardIndex shard) {
        return client.asGuest()
                     .select(InboxMessageHolder.class)
                     .where(eq(Column.shard(), shard), eq(Column.status(), TO_DELIVER))
                     .orderBy(Column.receivedAt(), DESCENDING)
                     .limit(1)
                     .run()
                     .stream()
                     .findFirst()
                     .map(InboxMessageHolder::getMessage);
    }

    private <C extends CommandMessage> void post(C command) {
        _trace().log("Posting command `%s`.", command.getClass());
        client.asGuest()
              .command(command)
              .onServerError((msg, error) -> logServerError(command, error))
              .postAndForget();
    }

    @SuppressWarnings("DuplicateStringLiteralInspection" /* Used in non-related module. */)
    private <C extends Message> void logServerError(C message, Error error) {
        _trace().log(
                "Server was not able to handle command `%s`: %s",
                message.getClass(), error
        );
    }
}
