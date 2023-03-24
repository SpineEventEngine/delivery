/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.grpc;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import io.spine.base.Time;
import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.message.delivery.grpc.OptionalInboxMessage;
import io.spine.message.delivery.grpc.ShardPickUpResult;
import io.spine.message.delivery.rejection.ShardAlreadyPickedUp;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.WorkerId;

import java.util.Optional;

/**
 * Utility for creating gRPC response messages.
 */
final class Responses {

    /**
     * Prevents this utility from instantiation.
     */
    private Responses() {
    }

    static ShardPickUpResult pickedUp(ShardIndex shard, WorkerId worker) {
        ShardPickedUp pickedUp = ShardPickedUp.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .setWhenPicked(Time.currentTime())
                .vBuild();
        return PickUpResults.pickedUp(pickedUp);
    }

    static ShardPickUpResult alreadyPickedUp(ShardIndex shard, WorkerId worker) {
        ShardAlreadyPickedUp alreadyPicked = ShardAlreadyPickedUp
                .newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .build();
        return PickUpResults.alreadyPickedUp(alreadyPicked.messageThrown());
    }

    /**
     * Completes the provided {@code observer}.
     *
     * <p>Sends {@linkplain Empty#getDefaultInstance() empty} response prior to completion.
     */
    static void completeCall(StreamObserver<Empty> observer) {
        observer.onNext(Empty.getDefaultInstance());
        observer.onCompleted();
    }

    /**
     * Writes the optional {@code message} if any is present and completes the {@code observer}.
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType" /* By intention. */)
    static void writeOptionalMessage(StreamObserver<OptionalInboxMessage> observer,
                                     Optional<InboxMessage> message) {
        OptionalInboxMessage.Builder responseBuilder = OptionalInboxMessage.newBuilder();
        message.ifPresent(responseBuilder::setMessage);
        OptionalInboxMessage response = responseBuilder.vBuild();
        observer.onNext(response);
        observer.onCompleted();
    }
}
