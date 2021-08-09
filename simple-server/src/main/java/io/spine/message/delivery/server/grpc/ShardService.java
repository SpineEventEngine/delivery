/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.grpc;

import com.google.common.base.Joiner;
import com.google.protobuf.Duration;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import io.spine.logging.Logging;
import io.spine.message.delivery.command.PickUpShard;
import io.spine.message.delivery.command.ReleaseExpiredSessions;
import io.spine.message.delivery.command.ReleaseShard;
import io.spine.message.delivery.event.ExpiredSessionsReleased;
import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.message.delivery.grpc.ShardServiceGrpc;
import io.spine.message.delivery.server.ExtendedShardRegistry;
import io.spine.server.NodeId;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.ShardProcessingSession;
import io.spine.server.storage.StorageFactory;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.message.delivery.server.grpc.Responses.alreadyPicked;
import static io.spine.message.delivery.server.grpc.Responses.completeCall;

/**
 * Acts as a gRPC-wired backend for the {@link io.spine.message.delivery.ShardSessionRegistry}.
 */
public final class ShardService extends ShardServiceGrpc.ShardServiceImplBase
        implements Logging, NamedHealthAwareService {

    private final ExtendedShardRegistry registry;
    private boolean healthy = true;

    /**
     * Creates a new {@code ShardService} backed by an {@link ExtendedShardRegistry} created from
     * the configured {@code factory}.
     */
    public ShardService(StorageFactory factory) {
        super();
        checkNotNull(factory);
        registry = new ExtendedShardRegistry(factory);
    }

    @Override
    public void pickShard(PickUpShard request, StreamObserver<ShardPickedUp> response) {
        ShardIndex shard = request.getShard();
        int index = shard.getIndex();
        NodeId worker = request.getWorker();
        Optional<ShardProcessingSession> session = registry.pickUp(shard, worker);
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
        ShardIndex shard = request.getShard();
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
        Duration period = request.getInactivityPeriod();
        Iterable<ShardIndex> indices = registry.releaseExpiredSessions(period);
        _debug().log("Expired sessions were released: %s.", Joiner.on(", ")
                                                                  .join(indices));

        responseObserver.onNext(ExpiredSessionsReleased.newBuilder()
                                        .vBuild());
    }

    @Override
    public boolean healthy() {
        return healthy;
    }

    @Override
    public void healthy(boolean value) {
        this.healthy = value;
    }

    @Override
    public String name() {
        return ShardServiceGrpc.SERVICE_NAME;
    }
}
