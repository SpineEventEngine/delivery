/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.grpc;

import com.google.protobuf.Duration;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import io.spine.base.Time;
import io.spine.logging.Logging;
import io.spine.message.delivery.grpc.command.PickUpShard;
import io.spine.message.delivery.grpc.command.ReleaseExpiredSessions;
import io.spine.message.delivery.grpc.command.ReleaseShard;
import io.spine.message.delivery.grpc.event.ExpiredSession;
import io.spine.message.delivery.grpc.event.ExpiredSessionsReleased;
import io.spine.message.delivery.grpc.LiquorPickUpOutcome;
import io.spine.message.delivery.grpc.ShardServiceGrpc;
import io.spine.message.delivery.grpc.rejection.ShardAlreadyPickedUp;
import io.spine.message.delivery.server.LiquorShardRegistry;
import io.spine.server.delivery.ShardSessionRecord;
import io.spine.server.storage.StorageFactory;

import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.message.delivery.LiquorPickUpOutcomes.alreadyPickedUp;
import static io.spine.message.delivery.LiquorPickUpOutcomes.pickedUp;
import static io.spine.message.delivery.server.grpc.Responses.completeCall;
import static io.spine.message.delivery.server.grpc.Responses.shardPickedUp;

/**
 * Acts as a gRPC-wired backend for the {@link io.spine.message.delivery.ShardSessionRegistry}.
 */
public final class ShardService extends ShardServiceGrpc.ShardServiceImplBase
        implements Logging, NamedHealthAwareService {

    private final LiquorShardRegistry registry;
    private final AtomicBoolean healthy = new AtomicBoolean(true);

    /**
     * Creates a new {@code ShardService} backed by {@link LiquorShardRegistry}.
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
        registry = new LiquorShardRegistry(factory, processingTimeout);
    }

    @Override
    public void pickShard(PickUpShard request, StreamObserver<LiquorPickUpOutcome> response) {
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
        _info().log(s, index);
    }

    @Override
    public void releaseSessions(ReleaseExpiredSessions request,
                                StreamObserver<ExpiredSessionsReleased> responseObserver) {
        var period = request.getInactivityPeriod();
        var sessions = registry.releaseInactiveSessions(period);
        _debug().log("Expired sessions were released: %s.", sessions);
        var result = ExpiredSessionsReleased.newBuilder();
        sessions.stream()
                .map(ShardService::toExpiredSession)
                .forEach(result::addShard);
        responseObserver.onNext(result.vBuild());
        responseObserver.onCompleted();
    }

    private static ExpiredSession toExpiredSession(ShardSessionRecord session) {
        return ExpiredSession.newBuilder()
                .setShard(session.getIndex())
                .setWorker(session.getWorker())
                .setWhenPicked(session.getWhenLastPicked())
                .setWhenReleased(Time.currentTime())
                .vBuild();
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
