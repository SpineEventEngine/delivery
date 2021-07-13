/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.grpc;

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Message;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.spine.base.Error;
import io.spine.client.Client;
import io.spine.client.Subscription;
import io.spine.logging.Logging;
import io.spine.message.delivery.command.PickUpShard;
import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.message.delivery.grpc.ShardSessionRegistryServiceGrpc;
import io.spine.message.delivery.rejection.Rejections;
import io.spine.message.delivery.rejection.ShardAlreadyPickedUp;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.grpc.Status.fromCode;
import static io.grpc.Status.fromThrowable;

/**
 * The {@code SessionRegistryService} allows client applications to interact with the
 * {@link io.spine.message.delivery.ShardSessionRegistry ShardSessionRegistry}.
 */
public final class SessionRegistryService
        extends ShardSessionRegistryServiceGrpc.ShardSessionRegistryServiceImplBase
        implements Logging {

    private final Client client;

    /**
     * Creates a new service which uses supplied {@code client} to talk to the
     * {@link io.spine.message.delivery.server.DeliveryContext DeliveryContext}.
     */
    public SessionRegistryService(Client client) {
        this.client = checkNotNull(client);
    }

    @Override
    public void pickShard(PickUpShard pickUpShard, StreamObserver<ShardPickedUp> responseObserver) {
        _trace().log(
                "Posting internal `PickUpShard` command and waiting for `ShardPickedUp` event."
        );
        CountDownLatch latch = new CountDownLatch(1);
        ImmutableSet<Subscription> subscriptions =
                client.asGuest()
                      .command(pickUpShard)
                      .observe(ShardPickedUp.class, e -> {
                          _trace().log(
                                  "Received `ShardPickedUp` event for shard `%s`.",
                                  e.getShard()
                          );
                          responseObserver.onNext(e);
                          responseObserver.onCompleted();
                          latch.countDown();
                      })
                      .observe(Rejections.ShardAlreadyPickedUp.class, e -> {
                          _trace().log(
                                  "Received `ShardAlreadyPickedUp` rejection for shard `%s`.",
                                  e.getShard()
                          );
                          responseObserver.onError(fromThrowable(
                                  ShardAlreadyPickedUp.newBuilder()
                                          .setShard(e.getShard())
                                          .setWorker(e.getWorker())
                                          .build()
                          ).asException());
                          latch.countDown();
                      })
                      .onServerError((msg, error) -> {
                          logServerError(msg, error);
                          responseObserver.onError(fromCode(Status.Code.INTERNAL).asException());
                          latch.countDown();
                      })
                      .onStreamingError((error) -> {
                          responseObserver.onError(fromThrowable(error).asException());
                          latch.countDown();
                      })
                      .post();
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
        subscriptions.forEach(client.subscriptions()::cancel);
    }

    @SuppressWarnings("DuplicateStringLiteralInspection" /* Used in non-related module. */)
    private <C extends Message> void logServerError(C message, Error error) {
        _trace().log(
                "Server was not able to handle command `%s`: %s",
                message.getClass(), error
        );
    }
}
