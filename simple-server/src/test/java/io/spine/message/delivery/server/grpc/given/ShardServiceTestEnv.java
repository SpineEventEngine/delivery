/*
 * Copyright (c) 2000-2022 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.grpc.given;

import com.google.protobuf.Duration;
import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.spine.message.delivery.command.PickUpShard;
import io.spine.message.delivery.command.ReleaseExpiredSessions;
import io.spine.message.delivery.command.ReleaseShard;
import io.spine.message.delivery.event.ExpiredSession;
import io.spine.message.delivery.event.ExpiredSessionsReleased;
import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.message.delivery.grpc.ShardServiceGrpc;
import io.spine.message.delivery.server.grpc.ShardService;
import io.spine.server.NodeId;
import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.DeliveryStrategy;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.WorkerId;
import io.spine.server.storage.memory.InMemoryStorageFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import static io.spine.base.Identifier.newUuid;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test environment for
 * {@link io.spine.message.delivery.server.grpc.ShardServiceTest ShardServiceTest}.
 */
public final class ShardServiceTestEnv implements Closeable {

    private static final NodeId node = ServerEnvironment.instance().nodeId();
    private static final ShardIndex shard = DeliveryStrategy.newIndex(0, 1);
    private static final WorkerId worker = WorkerId.newBuilder()
            .setNodeId(node)
            .setValue(newUuid())
            .vBuild();

    private final Collection<Server> runningServers = new ArrayList<>();

    /**
     * Returns blocking {@link ShardService} with the given {@code processingTimeout} specified.
     */
    public ShardServiceGrpc.ShardServiceBlockingStub syncShardService(Duration processingTimeout) {
        var shardService = new ShardService(InMemoryStorageFactory.newInstance(),
                                            processingTimeout);
        var channel = startServerWith(shardService);
        var service = ShardServiceGrpc.newBlockingStub(channel);
        return service;
    }

    private ManagedChannel startServerWith(BindableService service) {
        var serverName = InProcessServerBuilder.generateName();
        var server = InProcessServerBuilder.forName(serverName)
                                           .directExecutor()
                                           .addService(service)
                                           .build();
        try {
            server.start();
            runningServers.add(server);
        } catch (IOException cause) {
            fail("Failed to start a test server with `ShardService`.", cause);
        }
        var channel = InProcessChannelBuilder.forName(serverName)
                                             .directExecutor()
                                             .build();
        return channel;
    }

    /**
     * Creates a new request to {@linkplain PickUpShard pick up a shard}.
     */
    public static PickUpShard pickUpShard() {
        return PickUpShard.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .vBuild();
    }

    /**
     * Creates a new request to {@linkplain ReleaseShard release a shard} that was picked up
     * by the given {@link PickUpShard pick up} request.
     */
    public static ReleaseShard release(PickUpShard request) {
        return ReleaseShard.newBuilder()
                .setShard(request.getShard())
                .setWorker(request.getWorker())
                .vBuild();
    }

    /**
     * Creates a new request to {@linkplain ReleaseExpiredSessions release expired sessions}
     * with the given {@code inactivityPeriod}.
     */
    public static ReleaseExpiredSessions releaseExpiredSessions(Duration inactivityPeriod) {
        return ReleaseExpiredSessions.newBuilder()
                .setInactivityPeriod(inactivityPeriod)
                .vBuild();
    }

    /**
     * Creates a new {@link ShardPickedUp} response, which could potentially be returned
     * as a result of the given {@code request}.
     */
    public static ShardPickedUp asPickedUp(PickUpShard request) {
        return ShardPickedUp.newBuilder()
                .setShard(request.getShard())
                .setWorker(request.getWorker())
                .buildPartial();
    }

    /**
     * Creates a new {@link ExpiredSessionsReleased} response, which could potentially be returned
     * as a result of releasing the given {@code pickedUp} shard.
     */
    public static ExpiredSessionsReleased asReleased(ShardPickedUp pickedUp) {
        var expiredSession = ExpiredSession.newBuilder()
                .setShard(pickedUp.getShard())
                .setWorker(pickedUp.getWorker())
                .buildPartial();
        return ExpiredSessionsReleased.newBuilder()
                .addShard(expiredSession)
                .buildPartial();
    }

    @Override
    public void close() {
        runningServers.forEach(Server::shutdownNow);
    }
}
