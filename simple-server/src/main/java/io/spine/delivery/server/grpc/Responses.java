/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.server.grpc;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import io.spine.base.Time;
import io.spine.delivery.event.ShardPickedUp;
import io.spine.delivery.OptionalInboxMessage;
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

    /**
     * Creates a new {@code ShardPickedUp} rejection message with the supplied {@code shard}
     * and {@code worker}.
     *
     * <p>The picked up time is set to the {@linkplain Time#currentTime() current time}.
     */
    static ShardPickedUp shardPickedUp(ShardIndex shard, WorkerId worker) {
        var pickedUp = ShardPickedUp.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .setWhenPicked(Time.currentTime())
                .build();
        return pickedUp;
    }

    /**
     * Completes the provided {@code observer}.
     *
     * <p>Sends an {@linkplain Empty#getDefaultInstance() empty} response prior to completion.
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
        var responseBuilder = OptionalInboxMessage.newBuilder();
        message.ifPresent(responseBuilder::setMessage);
        var response = responseBuilder.build();
        observer.onNext(response);
        observer.onCompleted();
    }
}
