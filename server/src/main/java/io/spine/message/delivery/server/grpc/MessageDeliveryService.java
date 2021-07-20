/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.grpc;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.Duration;
import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import io.grpc.stub.StreamObserver;
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
import io.spine.message.delivery.grpc.MessageDeliveryServiceGrpc;
import io.spine.message.delivery.grpc.OptionalInboxMessage;
import io.spine.message.delivery.grpc.PageOfMessages;
import io.spine.message.delivery.grpc.ReadMessagesSinceTime;
import io.spine.message.delivery.server.ExtendedInboxStorage;
import io.spine.message.delivery.server.ExtendedShardRegistry;
import io.spine.server.NodeId;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.InboxMessageId;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.ShardProcessingSession;
import io.spine.server.storage.memory.InMemoryStorageFactory;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;
import java.util.Optional;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.spine.message.delivery.server.grpc.Responses.alreadyPicked;
import static io.spine.message.delivery.server.grpc.Responses.completeCall;
import static io.spine.message.delivery.server.grpc.Responses.writeOptionalMessage;

/**
 * Acts as a gRPC-wired backend for the {@link io.spine.server.delivery.Delivery} storage.
 */
public class MessageDeliveryService
        extends MessageDeliveryServiceGrpc.MessageDeliveryServiceImplBase
        implements Logging {

    private final ExtendedInboxStorage inboxStorage;
    private final ExtendedShardRegistry registry;

    public MessageDeliveryService() {
        super();
        InMemoryStorageFactory factory = InMemoryStorageFactory.newInstance();
        inboxStorage = new ExtendedInboxStorage(factory, false);
        registry = new ExtendedShardRegistry();
    }

    @Override
    public void pickShard(PickUpShard request, StreamObserver<ShardPickedUp> response) {
        ShardIndex shard = request.getShard();
        NodeId worker = request.getWorker();
        Optional<ShardProcessingSession> session = registry.pickUp(shard, worker);
        if (session.isPresent()) {
            ShardPickedUp pickedUp = Responses.shardPickedUp(shard, worker);
            response.onNext(pickedUp);
            response.onCompleted();
        } else {
            alreadyPicked(response, shard, worker);
        }
    }

    @Override
    public void releaseSession(ReleaseShard request, StreamObserver<Empty> observer) {
        registry.releaseShard(request.getShard());
        completeCall(observer);
    }

    @Override
    public void releaseSessions(ReleaseExpiredSessions request,
                                StreamObserver<ExpiredSessionsReleased> responseObserver) {
        Duration period = request.getInactivityPeriod();
        Iterable<ShardIndex> indices = registry.releaseExpiredSessions(period);
        _debug().log("Expired sessions were released: %s.", Joiner.on(", ")
                                                                  .join(indices));

        responseObserver.onNext(ExpiredSessionsReleased.newBuilder().vBuild());
    }

    @Override
    public void writeOne(WriteMessage request, StreamObserver<Empty> observer) {
        InboxMessageId id = request.messageId();
        InboxMessage message = request.getMessage();
        inboxStorage.write(id, message);
        completeCall(observer);
    }

    @Override
    public void writeMany(WriteMessages request, StreamObserver<Empty> observer) {
        List<InboxMessage> messages = request.getMessageList();
        inboxStorage.writeBatch(messages);
        completeCall(observer);
    }

    @Override
    public void removeOne(RemoveMessage request, StreamObserver<Empty> observer) {
        inboxStorage.delete(request.messageId());
        completeCall(observer);
    }

    @Override
    public void removeMany(RemoveMessages request, StreamObserver<Empty> observer) {
        ImmutableList<InboxMessageId> ids =
                request.getMessageList()
                       .stream()
                       .map(InboxMessage::getId)
                       .collect(toImmutableList());
        inboxStorage.deleteAll(ids);
        completeCall(observer);
    }

    @Override
    public void findOne(InboxMessageId request, StreamObserver<OptionalInboxMessage> observer) {
        Optional<InboxMessage> result = inboxStorage.read(request);
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
        PageOfMessages result = responseBuilder.vBuild();
        observer.onNext(result);
        observer.onCompleted();
    }

    @Override
    public void newestMessageToDeliver(ShardIndex request,
                                       StreamObserver<OptionalInboxMessage> observer) {
        Optional<InboxMessage> message = inboxStorage.newestMessageToDeliver(request);
        writeOptionalMessage(observer, message);
    }
}
