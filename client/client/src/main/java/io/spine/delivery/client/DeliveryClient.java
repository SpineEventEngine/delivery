/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.client;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.spine.delivery.InboxServiceGrpc;
import io.spine.delivery.InboxServiceGrpc.InboxServiceBlockingStub;
import io.spine.delivery.OptionalInboxMessage;
import io.spine.delivery.ReadMessagesSinceTime;
import io.spine.delivery.ShardServiceGrpc;
import io.spine.delivery.ShardServiceGrpc.ShardServiceBlockingStub;
import io.spine.delivery.client.strategy.Propagate;
import io.spine.delivery.command.PickUpShard;
import io.spine.delivery.command.ReleaseExpiredSessions;
import io.spine.delivery.command.ReleaseShard;
import io.spine.delivery.command.RemoveMessage;
import io.spine.delivery.command.RemoveMessages;
import io.spine.delivery.command.WriteMessage;
import io.spine.delivery.command.WriteMessages;
import io.spine.delivery.event.ExpiredSessionsReleased;
import io.spine.logging.WithLogging;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.InboxMessageComparator;
import io.spine.server.delivery.InboxMessageId;
import io.spine.server.delivery.Page;
import io.spine.server.delivery.PickUpOutcome;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.WorkerId;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
 * A delivery client that performs all of its operation through {@code Inbox} and {@code Shard}
 * gRPC services.
 *
 * @see InboxServiceGrpc
 * @see ShardServiceGrpc
 */
@SuppressWarnings({"ResultOfMethodCallIgnored", "OverlyCoupledClass", "FutureReturnValueIgnored"})
public final class DeliveryClient
        implements InboxClient, SessionRegistryClient, WithLogging, AutoCloseable {

    /**
     * The number of seconds to wait for the graceful termination of the channel
     * when {@linkplain #close() closing} a client that owns it.
     */
    private static final int CHANNEL_SHUTDOWN_TIMEOUT_SECONDS = 5;

    /**
     * The prefix of the log messages produced by this class.
     */
    private static final String LOG_PREFIX =
            '[' + DeliveryClient.class.getSimpleName() + ']';

    private final ShardServiceBlockingStub shardService;
    private final InboxServiceBlockingStub inboxService;

    private final RequestExecutionStrategy requestExecutionStrategy;

    private final ManagedChannel channel;

    /**
     * Tells whether {@link #channel} was created by this client, and therefore
     * must be shut down by it.
     *
     * <p>A channel supplied by the caller is owned — and shut down — by the caller.
     */
    private final boolean ownsChannel;

    private DeliveryClient(ManagedChannel channel,
                           boolean ownsChannel,
                           RequestExecutionStrategy strategy) {
        logger().atDebug()
                .log(() -> format(
                        "%s Creating a new instance for the channel `%s`.",
                        LOG_PREFIX, channel
                ));
        shardService = ShardServiceGrpc.newBlockingStub(channel);
        inboxService = InboxServiceGrpc.newBlockingStub(channel);
        requestExecutionStrategy = strategy;
        this.channel = channel;
        this.ownsChannel = ownsChannel;
    }

    /**
     * Creates a new delivery client that connects to a gRPC server on the specified {@code host}
     * and {@code port} and uses the {@link Propagate} {@code RequestExecutionStrategy}.
     */
    public static DeliveryClient create(String host, int port) {
        return create(host, port, new Propagate());
    }

    /**
     * Creates a new delivery client that connects to a gRPC server on the specified {@code host}
     * and {@code port}, and with the given {@code RequestExecutionStrategy}.
     *
     * <p>The returned client owns the channel it creates:
     * {@linkplain #close() closing} the client shuts the channel down.
     */
    public static DeliveryClient create(String host,
                                        int port,
                                        RequestExecutionStrategy strategy) {
        checkNotEmptyOrBlank(host);
        checkPositive(port);
        var channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();
        return new DeliveryClient(channel, true, strategy);
    }

    /**
     * Creates a new delivery client that connects to a gRPC server
     * using the specified {@code channel} and {@link Propagate} {@code RequestExecutionStrategy}.
     */
    public static DeliveryClient create(ManagedChannel channel) {
        return create(channel, new Propagate());
    }

    /**
     * Creates a new delivery client that connects to a gRPC server
     * using the specified {@code channel}, and with the given {@code RequestExecutionStrategy}.
     *
     * <p>The caller retains ownership of the {@code channel} — several clients may share it —
     * and is responsible for shutting it down once all of them are done.
     * {@linkplain #close() Closing} the returned client does not affect the channel.
     */
    public static DeliveryClient create(ManagedChannel channel,
                                        RequestExecutionStrategy strategy) {
        checkNotNull(channel);
        return new DeliveryClient(channel, false, strategy);
    }

    /**
     * Obtains the channel this client communicates over.
     */
    @VisibleForTesting
    ManagedChannel channel() {
        return channel;
    }

    /**
     * Closes this client, shutting down the channel if this client
     * {@linkplain #ownsChannel owns} it.
     *
     * <p>The shutdown is graceful: in-flight calls are given
     * {@value #CHANNEL_SHUTDOWN_TIMEOUT_SECONDS} seconds to complete before
     * the channel is terminated forcefully.
     *
     * <p>Closing a client created over a caller-supplied channel is a no-op:
     * such a channel may be shared with other clients, and only its owner
     * knows when all of them are done with it.
     */
    @Override
    public void close() {
        if (!ownsChannel) {
            return;
        }
        channel.shutdown();
        try {
            if (!channel.awaitTermination(CHANNEL_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                channel.shutdownNow();
            }
        } catch (InterruptedException e) {
            channel.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses the {@link RequestExecutionStrategy} to execute this request.
     *
     * @throws ExecutionFailedException
     *         if there were some issues that the chosen {@code RequestExecutionStrategy}
     *         could not handle
     */
    @Override
    public void writeMessage(InboxMessage message) throws ExecutionFailedException {
        checkNotDefaultArg(message);
        var writeMessage = WriteMessage.newBuilder()
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
     *         if there were some issues that the chosen {@code RequestExecutionStrategy}
     *         could not handle
     */
    @Override
    public void writeMessages(ShardIndex shard, Iterable<InboxMessage> messages)
            throws ExecutionFailedException {
        checkNotDefaultArg(shard);
        checkNotNull(messages);
        var writeMessages = WriteMessages.newBuilder()
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
     *         if there were some issues that the chosen {@code RequestExecutionStrategy}
     *         could not handle
     */
    @Override
    public void removeMessage(InboxMessage message) throws ExecutionFailedException {
        checkNotDefaultArg(message);
        var removeMessage = RemoveMessage.newBuilder()
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
     *         if there were some issues that the chosen {@code RequestExecutionStrategy}
     *         could not handle
     */
    @Override
    public void removeMessages(ShardIndex shard, Iterable<InboxMessage> messages)
            throws ExecutionFailedException {
        checkNotDefaultArg(shard);
        checkNotNull(messages);
        var removeMessages = RemoveMessages.newBuilder()
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
     *         if there were some issues that the chosen {@code RequestExecutionStrategy}
     *         could not handle
     */
    @Override
    public PickUpOutcome pickUpShard(ShardIndex shard, WorkerId worker)
            throws ExecutionFailedException {
        checkNotDefaultArg(shard);
        checkNotDefaultArg(worker);
        var pickUpShard = PickUpShard.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .build();
        try {
            var outcome = requestExecutionStrategy
                    .evaluate(() -> shardService.pickShard(pickUpShard));
            if (outcome.hasPickedUp()) {
                return pickedUp(fromEvent(outcome.getPickedUp()));
            } else {
                var rejection = outcome.getAlreadyPickedUp();
                return alreadyPicked(rejection.getWorker(), rejection.getWhenPicked());
            }
        } catch (ExecutionFailedException e) {
            var occurredExceptions = e.causes();
            Exception last = occurredExceptions.get(occurredExceptions.size() - 1);
            logger().atTrace()
                    .log(() -> format("%s Unable to pick up shard `%s`: %s.",
                                      LOG_PREFIX, shard, getStackTraceAsString(last)));
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses the {@link RequestExecutionStrategy} to execute this request.
     *
     * @throws ExecutionFailedException
     *         if there were some issues that the chosen {@code RequestExecutionStrategy}
     *         could not handle
     */
    @Override
    public void releaseShard(ShardIndex shard, WorkerId worker) throws ExecutionFailedException {
        checkNotDefaultArg(shard);
        checkNotDefaultArg(worker);
        var releaseShard = ReleaseShard.newBuilder()
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
     *         if there were some issues that the chosen {@code RequestExecutionStrategy}
     *         could not handle
     */
    @Override
    public ExpiredSessionsReleased releaseExpiredSessions(Duration inactivityPeriod)
            throws ExecutionFailedException {
        checkNotDefaultArg(inactivityPeriod);
        var command = ReleaseExpiredSessions.newBuilder()
                .setInactivityPeriod(inactivityPeriod)
                .build();
        logger().atTrace().log(
                () -> LOG_PREFIX + " Posting `ReleaseExpiredSessions` command" +
                        " and waiting for a response event `ExpiredSessionsReleased`."
        );
        var sessionsReleased =
                requestExecutionStrategy.evaluate(() -> shardService.releaseSessions(command));
        return sessionsReleased;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses the {@link RequestExecutionStrategy} to execute this request.
     *
     * @throws ExecutionFailedException
     *         if there were some issues that the chosen {@code RequestExecutionStrategy}
     *         could not handle
     */
    @Override
    public Optional<InboxMessage> find(InboxMessageId messageId) throws ExecutionFailedException {
        checkNotDefaultArg(messageId);

        var result =
                requestExecutionStrategy.evaluate(() -> inboxService.findOne(messageId));
        return asOptional(result);
    }

    private static Optional<InboxMessage> asOptional(OptionalInboxMessage result) {
        var message = result.getMessage();
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
     *         if there were some issues that the chosen {@code RequestExecutionStrategy}
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
     *         if there were some issues that the chosen {@code RequestExecutionStrategy}
     *         could not handle
     */
    @Override
    public ImmutableList<InboxMessage>
    readAll(ShardIndex shard, @Nullable Timestamp sinceWhen, int pageSize)
            throws ExecutionFailedException {
        checkNotDefaultArg(shard);
        checkPositive(pageSize);
        var queryBuilder = ReadMessagesSinceTime.newBuilder()
                .setShard(shard)
                .setPageSize(pageSize);
        if (sinceWhen != null) {
            queryBuilder.setSinceWhen(sinceWhen);
        }
        var query = queryBuilder.build();

        var page =
                requestExecutionStrategy.evaluate(() -> inboxService.findManyInShard(query));
        var result =
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
     *         if there were some issues that the chosen {@code RequestExecutionStrategy}
     *         could not handle
     */
    @Override
    public Optional<InboxMessage> newestMessageToDeliver(ShardIndex shard)
            throws ExecutionFailedException {
        var message = requestExecutionStrategy
                .evaluate(() -> inboxService.newestMessageToDeliver(shard));
        return asOptional(message);
    }
}
