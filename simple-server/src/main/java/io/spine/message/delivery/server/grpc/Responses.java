/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.grpc;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import io.spine.base.Time;
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

    /**
     * Populates the {@code response} with a {@code ShardAlreadyPickedUp} error.
     *
     * @param response
     *         the response on which the {@code onError} callback with the error is called
     * @param shard
     *         the shard that was already picked up
     * @param worker
     *         the worker who picked up the shard
     */
    static void
    alreadyPicked(StreamObserver<ShardPickedUp> response, ShardIndex shard, NodeId worker) {
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

    /**
     * Creates a new {@code ShardPickedUp} rejection message with the supplied {@code shard}
     * and {@code worker}.
     *
     * <p>The picked up time is set to the {@linkplain Time#currentTime() current time}.
     */
    static ShardPickedUp shardPickedUp(ShardIndex shard, NodeId worker) {
        ShardPickedUp pickedUp = ShardPickedUp.newBuilder()
                .setShard(shard)
                .setPickedBy(worker)
                .setWhenPicked(Time.currentTime())
                .vBuild();
        return pickedUp;
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
