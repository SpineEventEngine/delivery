/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.client;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.spine.logging.WithLogging;
import io.spine.delivery.client.strategy.Propagate;
import io.spine.delivery.command.PickUpShard;
import io.spine.delivery.command.ReleaseExpiredSessions;
import io.spine.delivery.command.ReleaseShard;
import io.spine.delivery.command.RemoveMessage;
import io.spine.delivery.command.RemoveMessages;
import io.spine.delivery.command.WriteMessage;
import io.spine.delivery.command.WriteMessages;
import io.spine.delivery.event.ExpiredSessionsReleased;
import io.spine.delivery.InboxServiceGrpc;
import io.spine.delivery.InboxServiceGrpc.InboxServiceBlockingStub;
import io.spine.delivery.DeliveryPickUpOutcome;
import io.spine.delivery.OptionalInboxMessage;
import io.spine.delivery.PageOfMessages;
import io.spine.delivery.ReadMessagesSinceTime;
import io.spine.delivery.ShardServiceGrpc;
import io.spine.delivery.ShardServiceGrpc.ShardServiceBlockingStub;
import io.spine.delivery.rejection.Rejections;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.InboxMessageComparator;
import io.spine.server.delivery.InboxMessageId;
import io.spine.server.delivery.Page;
import io.spine.server.delivery.PickUpOutcome;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.WorkerId;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Throwables.getStackTraceAsString;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.spine.delivery.client.ShardSessionRecords.fromEvent;
import static io.spine.protobuf.Messages.isDefault;
import static io.spine.server.delivery.PickUpOutcomeMixin.alreadyPicked;
import static io.spine.server.delivery.PickUpOutcomeMixin.pickedUp;
import static io.spine.util.Preconditions2.checkNotDefaultArg;
import static io.spine.util.Preconditions2.checkNotEmptyOrBlank;
import static io.spine.util.Preconditions2.checkPositive;
import static java.lang.String.format;

/**
 * A delivery client which performs all of its operation through {@code Inbox} and {@code Shard}
 * gRPC services.
 *
 * @see InboxServiceGrpc
 * @see ShardServiceGrpc
 */
@SuppressWarnings({"ResultOfMethodCallIgnored", "OverlyCoupledClass", "FutureReturnValueIgnored"})
public final class SimpleDeliveryClient
        implements InboxClient, SessionRegistryClient, WithLogging {

    private final ShardServiceBlockingStub shardService;
    private final InboxServiceBlockingStub inboxService;

    private final RequestExecutionStrategy requestExecutionStrategy;

    private SimpleDeliveryClient(ManagedChannel channel, RequestExecutionStrategy strategy) {
        logger().atDebug()
                .log(() -> format(
                        "Creating a `SimpleDeliveryClient` for the channel `%s`.", channel
                ));
        shardService = ShardServiceGrpc.newBlockingStub(channel);
        inboxService = InboxServiceGrpc.newBlockingStub(channel);
        requestExecutionStrategy = strategy;
    }

    /**
     * Creates a new delivery client which connects to a gRPC server on the specified {@code host}
     * and {@code port} and uses the {@link Propagate} {@code RequestExecutionStrategy}.
     */
    public static SimpleDeliveryClient create(String host, int port) {
        return create(host, port, new Propagate());
    }

    /**
     * Creates a new delivery client which connects to a gRPC server on the specified {@code host}
     * and {@code port}, and with the given {@code RequestExecutionStrategy}.
     */
    public static SimpleDeliveryClient create(String host, int port, RequestExecutionStrategy strategy) {
        checkNotEmptyOrBlank(host);
        checkPositive(port);
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();
        return new SimpleDeliveryClient(channel, strategy);
    }

    /**
     * Creates a new delivery client which connects to a gRPC server
     * using specified {@code channel} and {@link Propagate} {@code RequestExecutionStrategy}.
     */
    public static SimpleDeliveryClient create(ManagedChannel channel) {
        return create(channel, new Propagate());
    }

    /**
     * Creates a new delivery client which connects to a gRPC server
     * using specified {@code channel}, and with the given {@code RequestExecutionStrategy}.
     */
    public static SimpleDeliveryClient create(ManagedChannel channel,
                                              RequestExecutionStrategy strategy) {
        checkNotNull(channel);
        return new SimpleDeliveryClient(channel, strategy);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses the {@link RequestExecutionStrategy} to execute this request.
     *
     * @throws ExecutionFailedException
     *         if there were some issues that chosen {@code RequestExecutionStrategy}
     *         could not handle
     */
    @Override
    public void writeMessage(InboxMessage message) throws ExecutionFailedException {
        checkNotDefaultArg(message);
        WriteMessage writeMessage = WriteMessage.newBuilder()
                .setMessage(message)
                .build();
        requestExecutionStrategy.evaluate(() -> inboxService.writeOne(writeMessage));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses the {@link RequestExecutionStrategy} to execute this request.
     *
     * @throws ExecutionFailedException
     *         if there were some issues that chosen {@code RequestExecutionStrategy}
     *         could not handle
     */
    @Override
    public void writeMessages(ShardIndex shard, Iterable<InboxMessage> messages)
            throws ExecutionFailedException {
        checkNotDefaultArg(shard);
        checkNotNull(messages);
        WriteMessages writeMessages = WriteMessages.newBuilder()
                .setShard(shard)
                .addAllMessage(messages)
                .build();
        requestExecutionStrategy.evaluate(() -> inboxService.writeMany(writeMessages));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses the {@link RequestExecutionStrategy} to execute this request.
     *
     * @throws ExecutionFailedException
     *         if there were some issues that chosen {@code RequestExecutionStrategy}
     *         could not handle
     */
    @Override
    public void removeMessage(InboxMessage message) throws ExecutionFailedException {
        checkNotDefaultArg(message);
        RemoveMessage removeMessage = RemoveMessage.newBuilder()
                .setMessage(message)
                .build();
        requestExecutionStrategy.evaluate(() -> inboxService.removeOne(removeMessage));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses the {@link RequestExecutionStrategy} to execute this request.
     *
     * @throws ExecutionFailedException
     *         if there were some issues that chosen {@code RequestExecutionStrategy}
     *         could not handle
     */
    @Override
    public void removeMessages(ShardIndex shard, Iterable<InboxMessage> messages)
            throws ExecutionFailedException {
        checkNotDefaultArg(shard);
        checkNotNull(messages);
        RemoveMessages removeMessages = RemoveMessages.newBuilder()
                .setShard(shard)
                .addAllMessage(messages)
                .build();
        requestExecutionStrategy.evaluate(() -> inboxService.removeMany(removeMessages));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses the {@link RequestExecutionStrategy} to execute this request.
     *
     * @throws ExecutionFailedException
     *         if there were some issues that chosen {@code RequestExecutionStrategy}
     *         could not handle
     */
    @Override
    public PickUpOutcome pickUpShard(ShardIndex shard, WorkerId worker)
            throws ExecutionFailedException {
        checkNotDefaultArg(shard);
        checkNotDefaultArg(worker);
        PickUpShard pickUpShard = PickUpShard.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .build();
        try {
            DeliveryPickUpOutcome outcome = requestExecutionStrategy
                    .evaluate(() -> shardService.pickShard(pickUpShard));
            if (outcome.hasPickedUp()) {
                return pickedUp(fromEvent(outcome.getPickedUp()));
            } else {
                Rejections.ShardAlreadyPickedUp rejection = outcome.getAlreadyPickedUp();
                return alreadyPicked(rejection.getWorker(), rejection.getWhenPicked());
            }
        } catch (ExecutionFailedException e) {
            ImmutableList<RuntimeException> occurredExceptions = e.causes();
            Exception last = occurredExceptions.get(occurredExceptions.size() - 1);
            logger().atTrace()
                    .log(() -> format("[SimpleClient] Unable to pick up shard `%s`: %s.",
                                      shard, getStackTraceAsString(last)));
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses the {@link RequestExecutionStrategy} to execute this request.
     *
     * @throws ExecutionFailedException
     *         if there were some issues that chosen {@code RequestExecutionStrategy}
     *         could not handle
     */
    @Override
    public void releaseShard(ShardIndex shard, WorkerId worker) throws ExecutionFailedException {
        checkNotDefaultArg(shard);
        checkNotDefaultArg(worker);
        ReleaseShard releaseShard = ReleaseShard.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .build();
        requestExecutionStrategy.evaluate(() -> shardService.releaseSession(releaseShard));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses the {@link RequestExecutionStrategy} to execute this request.
     *
     * @throws ExecutionFailedException
     *         if there were some issues that chosen {@code RequestExecutionStrategy}
     *         could not handle
     */
    @Override
    public ExpiredSessionsReleased releaseExpiredSessions(Duration inactivityPeriod)
            throws ExecutionFailedException {
        checkNotDefaultArg(inactivityPeriod);
        ReleaseExpiredSessions command = ReleaseExpiredSessions.newBuilder()
                .setInactivityPeriod(inactivityPeriod)
                .build();
        logger().atTrace().log(
                () -> "[SimpleClient] Posting `ReleaseExpiredSessions` command" +
                        " and waiting for a response event `ExpiredSessionsReleased`."
        );
        ExpiredSessionsReleased sessionsReleased =
                requestExecutionStrategy.evaluate(() -> shardService.releaseSessions(command));
        return sessionsReleased;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses the {@link RequestExecutionStrategy} to execute this request.
     *
     * @throws ExecutionFailedException
     *         if there were some issues that chosen {@code RequestExecutionStrategy}
     *         could not handle
     */
    @Override
    public Optional<InboxMessage> find(InboxMessageId messageId) throws ExecutionFailedException {
        checkNotDefaultArg(messageId);

        OptionalInboxMessage result =
                requestExecutionStrategy.evaluate(() -> inboxService.findOne(messageId));
        return asOptional(result);
    }

    @NonNull
    private static Optional<InboxMessage> asOptional(OptionalInboxMessage result) {
        InboxMessage message = result.getMessage();
        if (isDefault(message)) {
            return Optional.empty();
        } else {
            return Optional.of(message);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses the {@link RequestExecutionStrategy} to execute this request.
     *
     * @throws ExecutionFailedException
     *         if there were some issues that chosen {@code RequestExecutionStrategy}
     *         could not handle
     */
    @Override
    public Page<InboxMessage> readAll(ShardIndex shard, int pageSize)
            throws ExecutionFailedException {
        Page<InboxMessage> page = new InboxPage(
                sinceWhen -> readAll(shard, sinceWhen, pageSize)
        );
        return page;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses the {@link RequestExecutionStrategy} to execute this request.
     *
     * @throws ExecutionFailedException
     *         if there were some issues that chosen {@code RequestExecutionStrategy}
     *         could not handle
     */
    @Override
    public ImmutableList<InboxMessage>
    readAll(ShardIndex shard, @Nullable Timestamp sinceWhen, int pageSize)
            throws ExecutionFailedException {
        checkNotDefaultArg(shard);
        checkPositive(pageSize);
        ReadMessagesSinceTime.Builder queryBuilder = ReadMessagesSinceTime.newBuilder()
                .setShard(shard)
                .setPageSize(pageSize);
        if (sinceWhen != null) {
            queryBuilder.setSinceWhen(sinceWhen);
        }
        ReadMessagesSinceTime query = queryBuilder.build();

        PageOfMessages page =
                requestExecutionStrategy.evaluate(() -> inboxService.findManyInShard(query));
        ImmutableList<InboxMessage> result =
                page.getMessageList()
                    .stream()
                    .sorted(InboxMessageComparator.chronologically)
                    .collect(toImmutableList());
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses the {@link RequestExecutionStrategy} to execute this request.
     *
     * @throws ExecutionFailedException
     *         if there were some issues that chosen {@code RequestExecutionStrategy}
     *         could not handle
     */
    @Override
    public Optional<InboxMessage> newestMessageToDeliver(ShardIndex shard)
            throws ExecutionFailedException {
        OptionalInboxMessage message = requestExecutionStrategy
                .evaluate(() -> inboxService.newestMessageToDeliver(shard));
        return asOptional(message);
    }
}
