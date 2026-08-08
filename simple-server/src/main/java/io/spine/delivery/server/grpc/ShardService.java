/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.server.grpc;

import com.google.protobuf.Duration;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import io.spine.base.Time;
import io.spine.logging.WithLogging;
import static java.lang.String.format;
import io.spine.delivery.command.PickUpShard;
import io.spine.delivery.command.ReleaseExpiredSessions;
import io.spine.delivery.command.ReleaseShard;
import io.spine.delivery.event.ExpiredSession;
import io.spine.delivery.event.ExpiredSessionsReleased;
import io.spine.delivery.DeliveryPickUpOutcome;
import io.spine.delivery.ShardServiceGrpc;
import io.spine.delivery.rejection.ShardAlreadyPickedUp;
import io.spine.delivery.server.DeliveryShardRegistry;
import io.spine.server.delivery.ShardSessionRecord;
import io.spine.server.storage.StorageFactory;

import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.delivery.DeliveryPickUpOutcomes.alreadyPickedUp;
import static io.spine.delivery.DeliveryPickUpOutcomes.pickedUp;
import static io.spine.delivery.server.grpc.Responses.completeCall;
import static io.spine.delivery.server.grpc.Responses.shardPickedUp;

/**
 * Acts as a gRPC-wired backend for the {@link io.spine.delivery.ShardSessionRegistry}.
 */
public final class ShardService extends ShardServiceGrpc.ShardServiceImplBase
        implements WithLogging, NamedHealthAwareService {

    private final DeliveryShardRegistry registry;
    private final AtomicBoolean healthy = new AtomicBoolean(true);

    /**
     * Creates a new {@code ShardService} backed by {@link DeliveryShardRegistry}.
     *
     * @param factory
     *         storage to be used to store registry's records
     * @param processingTimeout
     *         maximum span of time during which a worker can process a shard
     */
    public ShardService(StorageFactory factory, Duration processingTimeout) {
        super();
        checkNotNull(factory);
        checkNotNull(processingTimeout);
        registry = new DeliveryShardRegistry(factory, processingTimeout);
    }

    @Override
    public void pickShard(PickUpShard request, StreamObserver<DeliveryPickUpOutcome> response) {
        var shard = request.getShard();
        int index = shard.getIndex();
        var worker = request.getWorker();
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
        registry.releaseShard(shard);
        log("Shard %d released.", shard.getIndex());
        completeCall(observer);
    }

    private void log(String s, int index) {
        logger().atInfo().log(() -> format(s, index));
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
