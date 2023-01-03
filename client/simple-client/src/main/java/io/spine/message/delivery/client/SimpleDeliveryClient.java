/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.spine.logging.Logging;
import io.spine.message.delivery.command.PickUpShard;
import io.spine.message.delivery.command.ReleaseExpiredSessions;
import io.spine.message.delivery.command.ReleaseShard;
import io.spine.message.delivery.command.RemoveMessage;
import io.spine.message.delivery.command.RemoveMessages;
import io.spine.message.delivery.command.WriteMessage;
import io.spine.message.delivery.command.WriteMessages;
import io.spine.message.delivery.event.ExpiredSessionsReleased;
import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.message.delivery.grpc.InboxServiceGrpc;
import io.spine.message.delivery.grpc.InboxServiceGrpc.InboxServiceBlockingStub;
import io.spine.message.delivery.grpc.OptionalInboxMessage;
import io.spine.message.delivery.grpc.PageOfMessages;
import io.spine.message.delivery.grpc.ReadMessagesSinceTime;
import io.spine.message.delivery.grpc.ShardServiceGrpc;
import io.spine.message.delivery.grpc.ShardServiceGrpc.ShardServiceBlockingStub;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.InboxMessageComparator;
import io.spine.server.delivery.InboxMessageId;
import io.spine.server.delivery.Page;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.WorkerId;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.spine.protobuf.Messages.isDefault;
import static io.spine.util.Preconditions2.checkNotDefaultArg;
import static io.spine.util.Preconditions2.checkNotEmptyOrBlank;
import static io.spine.util.Preconditions2.checkPositive;

/**
 * A delivery client which performs all of its operation through {@code Inbox} and {@code Shard}
 * gRPC services.
 *
 * @see InboxServiceGrpc
 * @see ShardServiceGrpc
 */
@SuppressWarnings({"ResultOfMethodCallIgnored", "OverlyCoupledClass", "FutureReturnValueIgnored"})
public final class SimpleDeliveryClient
        implements InboxClient, SessionRegistryClient, Logging {

    private static final FluentLogger logger = Logging.loggerFor(SimpleDeliveryClient.class);

    private final ShardServiceBlockingStub shardService;
    private final InboxServiceBlockingStub inboxService;

    private SimpleDeliveryClient(ManagedChannel channel) {
        shardService = ShardServiceGrpc.newBlockingStub(channel);
        inboxService = InboxServiceGrpc.newBlockingStub(channel);
    }

    /**
     * Creates a new delivery client which connects to a gRPC server on the specified {@code host}
     * and {@code port}.
     */
    @SuppressWarnings("CheckReturnValue" /* We're fine to just `check` args. */)
    static SimpleDeliveryClient create(String host, int port) {
        checkNotEmptyOrBlank(host);
        checkPositive(port);
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();
        return new SimpleDeliveryClient(channel);
    }

    /**
     * Creates a new delivery client which connects to a gRPC server
     * using specified {@code channel}.
     */
    public static SimpleDeliveryClient create(ManagedChannel channel) {
        checkNotNull(channel);
        logger.atConfig()
              .log("Creating a `SimpleDeliveryClient` for the channel `%s`.", channel);
        return new SimpleDeliveryClient(channel);
    }

    @Override
    public void writeMessage(InboxMessage message) {
        checkNotDefaultArg(message);
        WriteMessage writeMessage = WriteMessage.newBuilder()
                .setMessage(message)
                .vBuild();
        inboxService.writeOne(writeMessage);
    }

    @Override
    public void writeMessages(ShardIndex shard, Iterable<InboxMessage> messages) {
        checkNotDefaultArg(shard);
        checkNotNull(messages);
        WriteMessages writeMessages = WriteMessages.newBuilder()
                .setShard(shard)
                .addAllMessage(messages)
                .vBuild();
        inboxService.writeMany(writeMessages);
    }

    @Override
    public void removeMessage(InboxMessage message) {
        checkNotDefaultArg(message);
        RemoveMessage removeMessage = RemoveMessage.newBuilder()
                .setMessage(message)
                .vBuild();
        inboxService.removeOne(removeMessage);
    }

    @Override
    public void removeMessages(ShardIndex shard, Iterable<InboxMessage> messages) {
        checkNotDefaultArg(shard);
        checkNotNull(messages);
        RemoveMessages removeMessages = RemoveMessages.newBuilder()
                .setShard(shard)
                .addAllMessage(messages)
                .vBuild();
        inboxService.removeMany(removeMessages);
    }

    @Override
    public Optional<ShardPickedUp> pickUpShard(ShardIndex shard, WorkerId worker) {
        checkNotDefaultArg(shard);
        checkNotDefaultArg(worker);
        PickUpShard pickUpShard = PickUpShard.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .vBuild();
        try {
            ShardPickedUp shardPickedUp = shardService.pickShard(pickUpShard);
            return Optional.of(shardPickedUp);
        } catch (StatusRuntimeException e) {
            _trace().log("[SimpleClient] Unable to pick up shard `%s`: %s.",
                         shard, e.getStatus());
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
        shardService.releaseSession(releaseShard);
    }

    @Override
    public ExpiredSessionsReleased releaseExpiredSessions(Duration inactivityPeriod) {
        checkNotDefaultArg(inactivityPeriod);
        ReleaseExpiredSessions command = ReleaseExpiredSessions.newBuilder()
                .setInactivityPeriod(inactivityPeriod)
                .vBuild();
        _trace().log(
                "[SimpleClient] Posting `ReleaseExpiredSessions` command" +
                        " and waiting for a response event `ExpiredSessionsReleased`."
        );
        ExpiredSessionsReleased sessionsReleased = shardService.releaseSessions(command);
        return sessionsReleased;
    }

    @Override
    public Optional<InboxMessage> find(InboxMessageId messageId) {
        checkNotDefaultArg(messageId);

        OptionalInboxMessage result = inboxService.findOne(messageId);
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

    @Override
    public Page<InboxMessage> readAll(ShardIndex shard, int pageSize) {
        Page<InboxMessage> page = new InboxPage(
                sinceWhen -> readAll(shard, sinceWhen, pageSize)
        );
        return page;
    }

    private ImmutableList<InboxMessage>
    readAll(ShardIndex shard, @Nullable Timestamp sinceWhen, int pageSize) {
        ReadMessagesSinceTime.Builder queryBuilder = ReadMessagesSinceTime.newBuilder()
                .setShard(shard)
                .setPageSize(pageSize);
        if (sinceWhen != null) {
            queryBuilder.setSinceWhen(sinceWhen);
        }
        ReadMessagesSinceTime query = queryBuilder.vBuild();

        PageOfMessages page = inboxService.findManyInShard(query);
        ImmutableList<InboxMessage> result =
                page.getMessageList()
                    .stream()
                    .sorted(InboxMessageComparator.chronologically)
                    .collect(toImmutableList());
        return result;
    }

    @Override
    public Optional<InboxMessage> newestMessageToDeliver(ShardIndex shard) {
        OptionalInboxMessage message = inboxService.newestMessageToDeliver(shard);
        return asOptional(message);
    }
}
