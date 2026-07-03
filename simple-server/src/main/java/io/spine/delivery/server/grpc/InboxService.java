/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.server.grpc;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import io.grpc.stub.StreamObserver;
import io.spine.logging.Logging;
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
import org.checkerframework.checker.nullness.qual.Nullable;

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
        implements Logging, NamedHealthAwareService {

    private final ExtendedInboxStorage inboxStorage;
    private final AtomicBoolean healthy = new AtomicBoolean(true);

    /**
     * Creates a {@code InboxService} backed by an {@link ExtendedInboxStorage} created from
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
        InboxMessageId id = request.messageId();
        InboxMessage message = request.getMessage();
        inboxStorage.write(id, message);
        completeCall(observer);
    }

    @Override
    public void writeMany(WriteMessages request, StreamObserver<Empty> observer) {
        log("`writeMany()`");
        List<InboxMessage> messages = request.getMessageList();
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
        ImmutableList<InboxMessageId> ids =
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
        Optional<InboxMessage> result = inboxStorage.read(id);
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
        int pageSize = request.getPageSize();
        ShardIndex shard = request.getShard();
        ImmutableList<InboxMessage> messages =
                inboxStorage.readAll(shard, sinceWhen, pageSize);
        PageOfMessages.Builder responseBuilder =
                PageOfMessages.newBuilder()
                        .addAllMessage(messages);
        log(shard, messages);
        PageOfMessages result = responseBuilder.build();
        observer.onNext(result);
        observer.onCompleted();
    }

    @Override
    public void newestMessageToDeliver(ShardIndex request,
                                       StreamObserver<OptionalInboxMessage> observer) {
        Optional<InboxMessage> message = inboxStorage.newestMessageToDeliver(request);
        writeOptionalMessage(observer, message);
    }

    private void log(String s) {
        _info().log(s);
    }

    private void log(ShardIndex shard, ImmutableList<InboxMessage> messages) {
        _info().log("`findManyInShard(%d)` -> %d.", shard.getIndex(), messages.size());
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
