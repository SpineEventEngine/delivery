/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.protobuf.Duration;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.spine.base.CommandMessage;
import io.spine.base.Error;
import io.spine.client.Client;
import io.spine.client.CommandRequest;
import io.spine.client.OrderBy;
import io.spine.client.QueryFilter;
import io.spine.client.QueryRequest;
import io.spine.logging.Logging;
import io.spine.message.delivery.InboxMessageHolder;
import io.spine.message.delivery.InboxMessageHolder.Column;
import io.spine.message.delivery.command.PickUpShard;
import io.spine.message.delivery.command.ReleaseExpiredSessions;
import io.spine.message.delivery.command.ReleaseShard;
import io.spine.message.delivery.command.RemoveMessage;
import io.spine.message.delivery.command.RemoveMessages;
import io.spine.message.delivery.command.WriteMessage;
import io.spine.message.delivery.command.WriteMessages;
import io.spine.message.delivery.event.ExpiredSessionsReleased;
import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.message.delivery.grpc.ShardSessionRegistryServiceGrpc;
import io.spine.message.delivery.grpc.ShardSessionRegistryServiceGrpc.ShardSessionRegistryServiceBlockingStub;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.InboxMessageComparator;
import io.spine.server.delivery.InboxMessageId;
import io.spine.server.delivery.Page;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.WorkerId;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Throwables.getStackTraceAsString;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Iterables.toArray;
import static io.spine.client.OrderBy.Direction.DESCENDING;
import static io.spine.client.QueryFilter.eq;
import static io.spine.client.QueryFilter.gt;
import static io.spine.server.delivery.InboxMessageStatus.TO_DELIVER;
import static io.spine.util.Preconditions2.checkNotDefaultArg;
import static io.spine.util.Preconditions2.checkNotEmptyOrBlank;

/**
 * A client for working with the Message Delivery server.
 *
 * <p>Provides APIs for modifying and querying the remote state of the Message Delivery context.
 */
public final class DeliveryClient implements SessionRegistryClient, InboxClient, Logging {

    private static final FluentLogger logger = Logging.loggerFor(DeliveryClient.class);

    private final Client client;
    private final ShardSessionRegistryServiceBlockingStub sessionRegistry;
    private final RequestExecutionStrategy requestExecutionStrategy;

    private DeliveryClient(ManagedChannel channel, RequestExecutionStrategy strategy) {
        requestExecutionStrategy = strategy;
        client = Client
                .usingChannel(channel)
                .withGuestId("DeliveryClient")
                .build();
        sessionRegistry = ShardSessionRegistryServiceGrpc.newBlockingStub(channel);
    }

    /**
     * Creates a new delivery client which connects to a gRPC server on the specified {@code host}
     * and {@code port} and uses the {@link Propagate} {@code RequestExecutionStrategy}.
     */
    @SuppressWarnings("CheckReturnValue" /* We're fine to just `check` args. */)
    static DeliveryClient create(String host, int port) {
        return create(host, port, new Propagate());
    }

    /**
     * Creates a new delivery client which connects to a gRPC server on the specified {@code host}
     * and {@code port} and uses the given {@code RequestExecutionStrategy}.
     */
    static DeliveryClient create(String host, int port, RequestExecutionStrategy strategy) {
        checkNotEmptyOrBlank(host);
        checkArgument(port > 0, "A positive value expected. Encountered: %s.", port);
        checkNotNull(strategy);
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();
        return new DeliveryClient(channel, strategy);
    }

    /**
     * Creates a new delivery client which connects to a gRPC server
     * using specified {@code channel} and {@link Propagate} {@code RequestExecutionStrategy}.
     */
    public static DeliveryClient create(ManagedChannel channel) {
        return create(channel, new Propagate());
    }

    /**
     * Creates a new delivery client which connects to a gRPC server
     * using specified {@code channel} and uses the given {@code RequestExecutionStrategy}.
     */
    public static DeliveryClient create(ManagedChannel channel, RequestExecutionStrategy strategy) {
        checkNotNull(channel);
        checkNotNull(strategy);
        logger.atConfig()
              .log("Creating a `DeliveryClient` for the channel `%s`.", channel);
        return new DeliveryClient(channel, strategy);
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
    public Optional<ShardPickedUp> pickUpShard(ShardIndex shard, WorkerId worker) {
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

            ShardPickedUp shardPickedUp = requestExecutionStrategy
                    .runWithStrategy(() -> sessionRegistry.pickShard(pickUpShard));
            return Optional.of(shardPickedUp);
        } catch (ExecutionFailedException e) {
            ImmutableList<Exception> occurredExceptions = e.causes();
            Exception last = occurredExceptions.get(occurredExceptions.size() - 1);
            _trace().log("Unable to pick up shard `%s`: %s.", shard, getStackTraceAsString(last));
        }
        return Optional.empty();
    }

    @Override
    public void releaseShard(ShardIndex shard, WorkerId worker) {
        checkNotDefaultArg(shard);
        checkNotDefaultArg(worker);
        ReleaseShard releaseShard = ReleaseShard.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .vBuild();
        post(releaseShard);
    }

    @Override
    public ExpiredSessionsReleased releaseExpiredSessions(Duration inactivityPeriod) {
        checkNotDefaultArg(inactivityPeriod);
        ReleaseExpiredSessions releaseExpiredSessions = ReleaseExpiredSessions.newBuilder()
                .setInactivityPeriod(inactivityPeriod)
                .vBuild();
        _trace().log(
                "Posting `ReleaseExpiredSessions` command " +
                        "and waiting for a response event `ExpiredSessionsReleased`."
        );

        ExpiredSessionsReleased sessionsReleased = requestExecutionStrategy
                .runWithStrategy(() -> sessionRegistry.releaseSessions(releaseExpiredSessions));
        return sessionsReleased;
    }

    @Override
    public Optional<InboxMessage> find(InboxMessageId messageId) {
        checkNotDefaultArg(messageId);
        QueryRequest<InboxMessageHolder> request =
                client.asGuest()
                      .select(InboxMessageHolder.class)
                      .byId(messageId);
        List<InboxMessageHolder> result = requestExecutionStrategy.runWithStrategy(request::run);
        return result.stream()
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
        QueryRequest<InboxMessageHolder> request =
                client.asGuest()
                      .select(InboxMessageHolder.class)
                      .where(toArray(filters.build(), QueryFilter.class))
                      .limit(pageSize)
                      .orderBy(Column.receivedAt(), OrderBy.Direction.ASCENDING);
        ImmutableList<InboxMessageHolder> result =
                requestExecutionStrategy.runWithStrategy(request::run);
        return result.stream()
                     .map(InboxMessageHolder::getMessage)
                     .sorted(InboxMessageComparator.chronologically)
                     .collect(toImmutableList());

    }

    @Override
    public Optional<InboxMessage> newestMessageToDeliver(ShardIndex shard) {
        QueryRequest<InboxMessageHolder> request =
                client.asGuest()
                      .select(InboxMessageHolder.class)
                      .where(eq(Column.shard(), shard), eq(Column.status(), TO_DELIVER))
                      .orderBy(Column.receivedAt(), DESCENDING)
                      .limit(1);

        ImmutableList<InboxMessageHolder> result =
                requestExecutionStrategy.runWithStrategy(request::run);
        return result.stream()
                     .findFirst()
                     .map(InboxMessageHolder::getMessage);
    }

    private <C extends CommandMessage> void post(C command) {
        _trace().log("Posting command `%s`.", command.getClass());
        CommandRequest request =
                client.asGuest()
                      .command(command)
                      .onServerError((msg, error) -> logServerError(command, error));
        requestExecutionStrategy.runWithStrategy(request::postAndForget);
    }

    @SuppressWarnings("DuplicateStringLiteralInspection" /* Used in non-related module. */)
    private <C extends Message> void logServerError(C message, Error error) {
        _trace().log(
                "Server was not able to handle command `%s`: %s",
                message.getClass(), error
        );
    }
}
