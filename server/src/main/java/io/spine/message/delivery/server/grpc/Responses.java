/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.grpc;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import io.spine.base.Time;
import io.spine.message.delivery.event.MessageWritten;
import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.message.delivery.grpc.OptionalInboxMessage;
import io.spine.message.delivery.rejection.ShardAlreadyPickedUp;
import io.spine.server.NodeId;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.ShardIndex;

import java.util.Optional;

import static io.grpc.Status.FAILED_PRECONDITION;

/**
 * Utility for creating gRPC response messages.
 */
final class Responses {

    /**
     * Prevents this utility from instantiation.
     */
    private Responses() {
    }

    static void
    alreadyPicked(StreamObserver<ShardPickedUp> response,
                  ShardIndex shard,
                  NodeId worker) {
        var error = ShardAlreadyPickedUp.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .build();
        response.onError(
                FAILED_PRECONDITION
                        .withCause(error)
                        .withDescription("The shard has been already picked up.")
                        .asRuntimeException()
        );
    }

    static ShardPickedUp shardPickedUp(ShardIndex shard, NodeId worker) {
        ShardPickedUp pickedUp =
                ShardPickedUp.newBuilder().setShard(shard)
                                                     .setPickedBy(worker)
                                                     .setWhenPicked(Time.currentTime())
                                                     .vBuild();
        return pickedUp;
    }

    static void messageWritten(StreamObserver<MessageWritten> responseObserver,
                               InboxMessage message) {
        MessageWritten response = MessageWritten.newBuilder().setMessage(message)
                                                            .vBuild();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    static void completeCall(StreamObserver<Empty> observer) {
        observer.onNext(Empty.getDefaultInstance());
        observer.onCompleted();
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType") /* By intention. */
    static void writeOptionalMessage(StreamObserver<OptionalInboxMessage> observer,
                                     Optional<InboxMessage> message) {
        OptionalInboxMessage.Builder responseBuilder = OptionalInboxMessage.newBuilder();
        message.ifPresent(responseBuilder::setMessage);
        OptionalInboxMessage response = responseBuilder.vBuild();
        observer.onNext(response);
        observer.onCompleted();
    }
}
