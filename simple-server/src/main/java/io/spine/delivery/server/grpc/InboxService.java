/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.server.grpc;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import io.grpc.stub.StreamObserver;
import io.spine.logging.WithLogging;
import static java.lang.String.format;
import io.spine.delivery.command.RemoveMessage;
import io.spine.delivery.command.RemoveMessages;
import io.spine.delivery.command.WriteMessage;
import io.spine.delivery.command.WriteMessages;
import io.spine.delivery.InboxServiceGrpc;
import io.spine.delivery.OptionalInboxMessage;
import io.spine.delivery.PageOfMessages;
import io.spine.delivery.ReadMessagesSinceTime;
import io.spine.delivery.server.ExtendedInboxStorage;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.InboxMessageId;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.storage.StorageFactory;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.spine.delivery.server.grpc.Responses.completeCall;
import static io.spine.delivery.server.grpc.Responses.writeOptionalMessage;

/**
 * Acts as a gRPC-wired backend for the {@link io.spine.server.delivery.InboxStorage}.
 */
public final class InboxService extends InboxServiceGrpc.InboxServiceImplBase
        implements WithLogging, NamedHealthAwareService {

    private final ExtendedInboxStorage inboxStorage;
    private final AtomicBoolean healthy = new AtomicBoolean(true);

    /**
     * Creates an {@code InboxService} backed by an {@link ExtendedInboxStorage} created from
     * the configured {@code factory}.
     */
    public InboxService(StorageFactory factory) {
        super();
        checkNotNull(factory);
        inboxStorage = new ExtendedInboxStorage(factory, false);
    }

    @Override
    public void writeOne(WriteMessage request, StreamObserver<Empty> observer) {
        log("`writeOne()`");
        var id = request.messageId();
        var message = request.getMessage();
        inboxStorage.write(id, message);
        completeCall(observer);
    }

    @Override
    public void writeMany(WriteMessages request, StreamObserver<Empty> observer) {
        log("`writeMany()`");
        var messages = request.getMessageList();
        inboxStorage.writeBatch(messages);
        completeCall(observer);
    }

    @Override
    public void removeOne(RemoveMessage request, StreamObserver<Empty> observer) {
        log("`removeOne()`");
        inboxStorage.delete(request.messageId());
        completeCall(observer);
    }

    @Override
    public void removeMany(RemoveMessages request, StreamObserver<Empty> observer) {
        log("`removeMany()`");
        var ids =
                request.getMessageList()
                       .stream()
                       .map(InboxMessage::getId)
                       .collect(toImmutableList());
        inboxStorage.deleteAll(ids);
        completeCall(observer);
    }

    @Override
    public void findOne(InboxMessageId id, StreamObserver<OptionalInboxMessage> observer) {
        log("`findOne()`");
        var result = inboxStorage.read(id);
        writeOptionalMessage(observer, result);
    }

    @Override
    public void findManyInShard(ReadMessagesSinceTime request,
                                StreamObserver<PageOfMessages> observer) {
        @Nullable Timestamp sinceWhen = request.getSinceWhen();
        if (Timestamp.getDefaultInstance()
                     .equals(sinceWhen)) {
            sinceWhen = null;
        }
        var pageSize = request.getPageSize();
        var shard = request.getShard();
        var messages =
                inboxStorage.readAll(shard, sinceWhen, pageSize);
        var responseBuilder =
                PageOfMessages.newBuilder()
                        .addAllMessage(messages);
        log(shard, messages);
        var result = responseBuilder.build();
        observer.onNext(result);
        observer.onCompleted();
    }

    @Override
    public void newestMessageToDeliver(ShardIndex request,
                                       StreamObserver<OptionalInboxMessage> observer) {
        var message = inboxStorage.newestMessageToDeliver(request);
        writeOptionalMessage(observer, message);
    }

    private void log(String s) {
        logger().atInfo().log(() -> format(s));
    }

    private void log(ShardIndex shard, ImmutableList<InboxMessage> messages) {
        logger().atInfo().log(() -> format("`findManyInShard(%d)` -> %d.", shard.getIndex(), messages.size()));
    }

    @Override
    public boolean healthy() {
        return healthy.get();
    }

    @Override
    public void healthy(boolean value) {
        healthy.set(value);
    }

    @Override
    public String name() {
        return InboxServiceGrpc.SERVICE_NAME;
    }
}
