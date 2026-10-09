/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package io.spine.delivery.server.grpc;

import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import io.grpc.stub.StreamObserver;
import io.spine.delivery.InboxServiceGrpc;
import io.spine.delivery.OptionalInboxMessage;
import io.spine.delivery.PageOfMessages;
import io.spine.delivery.ReadMessagesSinceTime;
import io.spine.delivery.command.RemoveMessage;
import io.spine.delivery.command.RemoveMessages;
import io.spine.delivery.command.WriteMessage;
import io.spine.delivery.command.WriteMessages;
import io.spine.delivery.storage.InboxStore;
import io.spine.logging.WithLogging;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.InboxMessageId;
import io.spine.server.delivery.ShardIndex;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.spine.delivery.server.grpc.Responses.completeCall;
import static io.spine.delivery.server.grpc.Responses.writeOptionalMessage;
import static io.spine.delivery.storage.StoredForms.checkPageSize;
import static java.lang.String.format;

/**
 * Acts as a gRPC-wired backend for the {@link io.spine.server.delivery.InboxStorage} of
 * the client applications, on top of an {@link InboxStore}.
 *
 * <p>Each call checks its request, if needed, and then calls one operation of
 * the {@link InboxStore}.
 */
public final class InboxService extends InboxServiceGrpc.InboxServiceImplBase
        implements WithLogging, NamedHealthAwareService {

    /**
     * The store of the inbox messages.
     */
    private final InboxStore store;

    /**
     * Whether the service reports itself as serving.
     */
    private final AtomicBoolean healthy = new AtomicBoolean(true);

    /**
     * Creates an {@code InboxService} backed by the given store.
     */
    public InboxService(InboxStore store) {
        super();
        this.store = checkNotNull(store);
    }

    @Override
    public void writeOne(WriteMessage request, StreamObserver<Empty> observer) {
        log("`writeOne()`");
        store.write(List.of(request.getMessage()));
        completeCall(observer);
    }

    /**
     * Stores each message in the shard of its own ID. The {@code shard} field of the request
     * is not used.
     */
    @Override
    public void writeMany(WriteMessages request, StreamObserver<Empty> observer) {
        log("`writeMany()`");
        store.write(request.getMessageList());
        completeCall(observer);
    }

    /**
     * Removes the message with the ID of the message in the request. Nothing else of
     * the message in the request is used.
     */
    @Override
    public void removeOne(RemoveMessage request, StreamObserver<Empty> observer) {
        log("`removeOne()`");
        store.delete(List.of(request.messageId()));
        completeCall(observer);
    }

    /**
     * Removes the messages with the IDs of the messages in the request. Nothing else of
     * the messages, and not the {@code shard} field of the request, is used.
     */
    @Override
    public void removeMany(RemoveMessages request, StreamObserver<Empty> observer) {
        log("`removeMany()`");
        var ids =
                request.getMessageList()
                       .stream()
                       .map(InboxMessage::getId)
                       .collect(toImmutableList());
        store.delete(ids);
        completeCall(observer);
    }

    @Override
    public void findOne(InboxMessageId id, StreamObserver<OptionalInboxMessage> observer) {
        log("`findOne()`");
        var result = store.find(id);
        writeOptionalMessage(observer, Optional.ofNullable(result));
    }

    /**
     * Reads a page from the start of the shard if {@code since_when} is not set, or is set
     * to the zero timestamp.
     *
     * @throws IllegalArgumentException
     *         if {@code page_size} is not positive, which fails the call with
     *         the {@code UNKNOWN} status; it is the status that the earlier versions of
     *         the server returned for such a page size, so the clients see no change
     */
    @Override
    public void findManyInShard(ReadMessagesSinceTime request,
                                StreamObserver<PageOfMessages> observer) {
        var sinceWhen = request.getSinceWhen();
        if (Timestamp.getDefaultInstance()
                     .equals(sinceWhen)) {
            sinceWhen = null;
        }
        var pageSize = request.getPageSize();
        checkPageSize(pageSize);
        var shard = request.getShard();
        var messages = store.page(shard, sinceWhen, pageSize);
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
        var message = store.newestToDeliver(request);
        writeOptionalMessage(observer, Optional.ofNullable(message));
    }

    /**
     * Logs the given message at the {@code INFO} level.
     */
    private void log(String s) {
        logger().atInfo().log(() -> format(s));
    }

    /**
     * Logs the index of the shard and the size of the page read from it.
     */
    private void log(ShardIndex shard, List<InboxMessage> messages) {
        logger().atInfo()
                .log(() -> format("`findManyInShard(%d)` -> %d.",
                                  shard.getIndex(), messages.size()));
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
