/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.grpc;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import io.spine.base.Time;
import io.spine.logging.Logging;
import io.spine.message.delivery.command.PickUpShard;
import io.spine.message.delivery.command.ReleaseExpiredSessions;
import io.spine.message.delivery.command.ReleaseShard;
import io.spine.message.delivery.event.ExpiredSession;
import io.spine.message.delivery.event.ExpiredSessionsReleased;
import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.message.delivery.grpc.ShardServiceGrpc;
import io.spine.message.delivery.server.LiquorShardRegistry;
import io.spine.server.delivery.ShardSessionRecord;
import io.spine.server.storage.StorageFactory;

import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.message.delivery.server.grpc.Responses.alreadyPicked;
import static io.spine.message.delivery.server.grpc.Responses.completeCall;

/**
 * Acts as a gRPC-wired backend for the {@link io.spine.message.delivery.ShardSessionRegistry}.
 */
public final class ShardService extends ShardServiceGrpc.ShardServiceImplBase
        implements Logging, NamedHealthAwareService {

    private final LiquorShardRegistry registry;
    private final AtomicBoolean healthy = new AtomicBoolean(true);

    public ShardService(StorageFactory factory) {
        super();
        checkNotNull(factory);
        registry = new LiquorShardRegistry(factory);
    }

    @Override
    public void pickShard(PickUpShard request, StreamObserver<ShardPickedUp> response) {
        var shard = request.getShard();
        int index = shard.getIndex();
        var worker = request.getWorker();
        var session = registry.pickUp(shard, worker);
        if (session.isPresent()) {
            ShardPickedUp pickedUp = Responses.shardPickedUp(shard, worker);
            log("Shard %d picked up.", index);
            response.onNext(pickedUp);
            response.onCompleted();
        } else {
            log("Shard %d NOT available.", index);
            alreadyPicked(response, shard, worker);
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
