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

import com.google.protobuf.Duration;
import com.google.protobuf.Empty;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.spine.base.Time;
import io.spine.logging.WithLogging;
import io.spine.delivery.command.PickUpShard;
import io.spine.delivery.command.ReleaseExpiredSessions;
import io.spine.delivery.command.ReleaseShard;
import io.spine.delivery.event.ExpiredSession;
import io.spine.delivery.event.ExpiredSessionsReleased;
import io.spine.delivery.DeliveryPickUpOutcome;
import io.spine.delivery.ShardServiceGrpc;
import io.spine.delivery.rejection.ShardAlreadyPickedUp;
import io.spine.delivery.server.DeliveryShardRegistry;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.ShardSessionRecord;
import io.spine.server.delivery.WorkerId;
import io.spine.delivery.storage.ShardSessionStore;

import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.delivery.DeliveryPickUpOutcomes.alreadyPickedUp;
import static io.spine.delivery.DeliveryPickUpOutcomes.pickedUp;
import static io.spine.delivery.server.ReleaseOutcome.HELD_BY_ANOTHER_WORKER;
import static io.spine.delivery.server.ReleaseOutcome.RENEWED;
import static io.spine.delivery.server.grpc.Responses.completeCall;
import static io.spine.delivery.server.grpc.Responses.shardPickedUp;
import static io.spine.delivery.storage.Shards.tag;
import static java.lang.String.format;

/**
 * Acts as a gRPC-wired backend for the {@link io.spine.delivery.ShardSessionRegistry}.
 *
 * <p>A pick-up or release call that is cancelled before the service starts serving it is not
 * served. This narrows the window in which a late request can affect the newer requests of
 * the same worker. A call cancelled after the service started serving it is served as usual.
 */
public final class ShardService extends ShardServiceGrpc.ShardServiceImplBase
        implements NamedHealthAwareService, WithLogging {

    /**
     * Decides whether a shard may be picked up, and records the sessions.
     */
    private final DeliveryShardRegistry registry;

    /**
     * Whether the service reports itself as serving.
     */
    private final AtomicBoolean healthy = new AtomicBoolean(true);

    /**
     * Creates a new {@code ShardService} backed by a {@link DeliveryShardRegistry}.
     *
     * @param store
     *         the store of the registry's records
     * @param processingTimeout
     *         maximum span of time during which a worker can process a shard
     */
    public ShardService(ShardSessionStore store, Duration processingTimeout) {
        super();
        checkNotNull(store);
        checkNotNull(processingTimeout);
        registry = new DeliveryShardRegistry(store, processingTimeout);
    }

    @Override
    public void pickShard(PickUpShard request, StreamObserver<DeliveryPickUpOutcome> response) {
        var shard = request.getShard();
        var index = shard.getIndex();
        var worker = request.getWorker();
        if (endIfCancelled("pick up", shard, worker, response)) {
            return;
        }
        try {
            var session = registry.pickUp(shard, worker);
            var pickedUp = shardPickedUp(session.shardIndex(), worker);
            log("Shard %d picked up.", index);
            response.onNext(pickedUp(pickedUp));
            response.onCompleted();
        } catch (ShardAlreadyPickedUp e) {
            log("Shard %d NOT available.", index);
            response.onNext(alreadyPickedUp(e.messageThrown()));
            response.onCompleted();
        }
    }

    @Override
    public void releaseSession(ReleaseShard request, StreamObserver<Empty> observer) {
        var shard = request.getShard();
        var worker = request.getWorker();
        if (endIfCancelled("release", shard, worker, observer)) {
            return;
        }
        var outcome = registry.releaseShard(shard, worker);
        if (outcome == HELD_BY_ANOTHER_WORKER) {
            logger().atWarning().log(() -> format(
                    "The shard `%s` is held by another worker, not by the worker `%s`," +
                            " which asked to release it. The session is left intact.",
                    tag(shard), nameOf(worker)));
        }
        if (outcome == RENEWED) {
            logger().atWarning().log(() -> format(
                    "The worker `%s` picked the shard `%s` up again while its release was" +
                            " in progress. The newer session is left intact.",
                    nameOf(worker), tag(shard)));
        }
        var template = switch (outcome) {
            case RELEASED -> "Shard %d released.";
            case NOT_PICKED_UP -> "Shard %d NOT released: no worker holds it.";
            case HELD_BY_ANOTHER_WORKER -> "Shard %d NOT released: another worker holds it.";
            case RENEWED -> "Shard %d NOT released: its worker holds it in a newer session.";
        };
        log(template, shard.getIndex());
        completeCall(observer);
    }

    /**
     * If the current call is cancelled, ends it with {@code CANCELLED} without serving it.
     *
     * <p>A call may wait in the queue of the server's executor before it is served, and it may
     * be cancelled meanwhile: for example, when the client cancels it, when its deadline
     * passes, when its connection is reset, or when the server is forced to shut down. gRPC
     * cancels the context of such a call at once, even while the call waits. Serving the call
     * after that could harm the newer calls of the same worker. For example, a late release
     * could end the session that the worker started after its release failed.
     *
     * @param action
     *         the requested action, to name in the log
     * @param shard
     *         the shard of the request
     * @param worker
     *         the worker of the request
     * @param observer
     *         the observer of the call
     * @return {@code true} if the call is cancelled and now ended;
     *         {@code false} if it is to be served
     */
    private boolean endIfCancelled(String action,
                                   ShardIndex shard,
                                   WorkerId worker,
                                   StreamObserver<?> observer) {
        var context = Context.current();
        if (!context.isCancelled()) {
            return false;
        }
        var cause = context.cancellationCause();
        var because = cause != null ? format(" The cause: %s.", cause) : "";
        logger().atWarning().log(() -> format(
                "The call to %s the shard `%s` for the worker `%s` was cancelled before it was" +
                        " served, and is dropped.%s",
                action, tag(shard), nameOf(worker), because));
        observer.onError(Status.CANCELLED
                                 .withDescription("The call was cancelled before it was served.")
                                 .asRuntimeException());
        return true;
    }

    /**
     * Returns the name of the worker for a log message: {@code <value>@<node>}, where
     * {@code <value>} is the worker ID value, and {@code <node>} is the node ID value.
     */
    private static String nameOf(WorkerId worker) {
        return worker.getValue() + '@' + worker.getNodeId().getValue();
    }

    /**
     * Logs the message made of the given template and the index of the shard, at
     * the {@code DEBUG} level, so that serving requests does not write to the log by default.
     */
    private void log(String template, int index) {
        logger().atDebug().log(() -> format(template, index));
    }

    @Override
    public void releaseSessions(ReleaseExpiredSessions request,
                                StreamObserver<ExpiredSessionsReleased> responseObserver) {
        var period = request.getInactivityPeriod();
        var sessions = registry.releaseInactiveSessions(period);
        logger().atDebug().log(() -> format("Expired sessions were released: %s.", sessions));
        var result = ExpiredSessionsReleased.newBuilder();
        sessions.stream()
                .map(ShardService::toExpiredSession)
                .forEach(result::addShard);
        responseObserver.onNext(result.build());
        responseObserver.onCompleted();
    }

    /**
     * Describes the released session, as it was before the release, with the current time
     * as the time of the release.
     */
    private static ExpiredSession toExpiredSession(ShardSessionRecord session) {
        return ExpiredSession.newBuilder()
                .setShard(session.getIndex())
                .setWorker(session.getWorker())
                .setWhenPicked(session.getWhenLastPicked())
                .setWhenReleased(Time.currentTime())
                .build();
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
        return ShardServiceGrpc.SERVICE_NAME;
    }
}
