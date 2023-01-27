/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.grpc;

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Message;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.spine.base.Error;
import io.spine.client.Client;
import io.spine.client.Subscription;
import io.spine.logging.Logging;
import io.spine.message.delivery.command.PickUpShard;
import io.spine.message.delivery.command.ReleaseExpiredSessions;
import io.spine.message.delivery.event.ExpiredSessionsReleased;
import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.message.delivery.grpc.ShardSessionRegistryServiceGrpc;
import io.spine.message.delivery.rejection.Rejections;
import io.spine.message.delivery.rejection.ShardAlreadyPickedUp;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.nullToEmpty;
import static io.grpc.Status.FAILED_PRECONDITION;
import static io.grpc.Status.INTERNAL;
import static io.grpc.Status.fromCode;
import static io.grpc.Status.fromThrowable;
import static io.spine.util.Preconditions2.checkNotDefaultArg;
import static java.lang.String.format;

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
        super();
        this.client = checkNotNull(client);
    }

    @Override
    public void pickShard(PickUpShard pickUpShard, StreamObserver<ShardPickedUp> responseObserver) {
        _trace().log(
                "Posting internal `PickUpShard` command and waiting for `ShardPickedUp` event."
        );
        checkNotDefaultArg(pickUpShard);
        CountDownLatch latch = new CountDownLatch(1);
        Context ctx = Context.current()
                             .fork();
        ctx.run(() -> {
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
                              var msg = format(
                                      "Shard `%s` is already picked up by the worker `%s`.",
                                      e.getShard(), e.getWorker()
                              );
                              _trace().log(msg);
                              var error = ShardAlreadyPickedUp.newBuilder()
                                      .setShard(e.getShard())
                                      .setWorker(e.getWorker())
                                      .build();
                              responseObserver.onError(
                                      FAILED_PRECONDITION
                                              .withCause(error)
                                              .withDescription(msg)
                                              .asRuntimeException()
                              );
                              latch.countDown();
                          })
                          .onServerError((msg, error) -> {
                              logServerError(msg, error);
                              responseObserver.onError(
                                      fromCode(Status.Code.INTERNAL).asException());
                              latch.countDown();
                          })
                          .onStreamingError((error) -> {
                              if (!ignoreCancelledStream(error)) {
                                  _trace().withCause(error)
                                          .log("gRPC streaming error occurred while " +
                                                       "picking up shard `%s`.",
                                               pickUpShard.getShard()
                                          );
                                  responseObserver.onError(fromThrowable(error).asException());
                              }
                              latch.countDown();
                          })
                          .post();
            try {
                latch.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            cancelSubscription(subscriptions);
        });
    }

    @Override
    public void releaseSessions(ReleaseExpiredSessions releaseSessions,
                                StreamObserver<ExpiredSessionsReleased> responseObserver) {
        _trace().log(
                "Posting internal `ReleaseExpiredSessions` command " +
                        "and waiting for `ExpiredSessionsReleased` event."
        );
        checkNotDefaultArg(releaseSessions);
        CountDownLatch latch = new CountDownLatch(1);
        Context ctx = Context.current()
                             .fork();
        ctx.run(() -> {
            ImmutableSet<Subscription> subscriptions =
                    client.asGuest()
                          .command(releaseSessions)
                          .observe(ExpiredSessionsReleased.class, e -> {
                              _trace().log(
                                      "Received `ExpiredSessionsReleased` event with `%d` shards.",
                                      e.getShardCount()
                              );
                              responseObserver.onNext(e);
                              responseObserver.onCompleted();
                              latch.countDown();
                          })
                          .onServerError((msg, error) -> {
                              logServerError(msg, error);
                              responseObserver.onError(
                                      INTERNAL.withDescription(error.getMessage())
                                              .asRuntimeException()
                              );
                              latch.countDown();
                          })
                          .onStreamingError((error) -> {
                              if (!ignoreCancelledStream(error)) {
                                  _trace().withCause(error)
                                          .log("gRPC streaming error occurred " +
                                                       "while releasing expired sessions.");
                                  responseObserver.onError(fromThrowable(error).asException());
                              }
                              latch.countDown();
                          })
                          .post();
            try {
                latch.await(3, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            cancelSubscription(subscriptions);
        });
    }

    private void cancelSubscription(Iterable<Subscription> subscriptions) {
        _trace().log("Cancelling subscriptions.");
        subscriptions.forEach(client.subscriptions()::cancel);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted" /* For better clarity. */)
    private boolean ignoreCancelledStream(Throwable error) {
        if (!(error instanceof StatusRuntimeException)) {
            return false;
        }
        Status status = ((StatusRuntimeException) error).getStatus();
        if (Status.Code.CANCELLED != status.getCode()) {
            return false;
        }
        if (nullToEmpty(error.getMessage()).contains("without error")) {
            _trace().log("Stream is cancelled without errors.");
            return true;
        }
        return false;
    }

    @SuppressWarnings("DuplicateStringLiteralInspection" /* Used in non-related module. */)
    private <C extends Message> void logServerError(C message, Error error) {
        _trace().log(
                "Server was not able to handle command `%s`: %s",
                message.getClass(), error
        );
    }
}
