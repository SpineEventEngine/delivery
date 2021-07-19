/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.grpc;

import com.google.common.util.concurrent.Uninterruptibles;
import com.google.protobuf.util.Durations;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.spine.environment.Environment;
import io.spine.message.delivery.command.PickUpShard;
import io.spine.message.delivery.command.ReleaseExpiredSessions;
import io.spine.message.delivery.event.ExpiredSession;
import io.spine.message.delivery.event.ExpiredSessionsReleased;
import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.message.delivery.grpc.ShardSessionRegistryServiceGrpc;
import io.spine.message.delivery.grpc.ShardSessionRegistryServiceGrpc.ShardSessionRegistryServiceBlockingStub;
import io.spine.message.delivery.server.App;
import io.spine.server.NodeId;
import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.DeliveryStrategy;
import io.spine.server.delivery.ShardIndex;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("`SessionRegistryService` should")
final class SessionRegistryServiceTest {

    private final ShardIndex shard = DeliveryStrategy.newIndex(1, 2);
    private final NodeId worker = NodeId.newBuilder()
            .setValue(SessionRegistryServiceTest.class.getName())
            .vBuild();
    private final App app = new App();
    private @MonotonicNonNull ShardSessionRegistryServiceBlockingStub sessionRegistry;

    @AfterAll
    static void resetEnvs() {
        Environment.instance().reset();
        ServerEnvironment.instance().reset();
    }

    @BeforeEach
    void startApp() {
        var appThread = new Thread(app::initAndStart);
        appThread.start();
        sleepUninterruptibly(Duration.ofSeconds(3)); // allow the server to start.
    }

    @BeforeEach
    void setupClients() {
        var localServer = localServer();
        sessionRegistry = ShardSessionRegistryServiceGrpc.newBlockingStub(localServer);
    }

    @AfterEach
    void shutdownApp() {
        app.remoteGrpc()
           .shutdownNowAndWait();
        app.internalGrpc()
           .shutdownNowAndWait();
    }

    @Test
    @DisplayName("pick up a shard")
    void pickUpShard() {
        var request = PickUpShard.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .vBuild();
        var expected = ShardPickedUp.newBuilder()
                .setShard(shard)
                .setPickedBy(worker)
                .buildPartial();
        var response = sessionRegistry.pickShard(request);
        assertThat(response)
                .comparingExpectedFieldsOnly()
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("do not pick up a shard for delivery if one is already picked up")
    void notPickUpShard() {
        var request = PickUpShard.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .vBuild();
        var firstAttempt = sessionRegistry.pickShard(request);
        assertThat(firstAttempt)
                .isNotEqualToDefaultInstance();
        assertThrows(StatusRuntimeException.class, () -> sessionRegistry.pickShard(request));
    }

    @Test
    @DisplayName("release expired sessions")
    @SuppressWarnings("ResultOfMethodCallIgnored")
    void releaseExpiredSessions() {
        var pickShard = PickUpShard.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .vBuild();
        sessionRegistry.pickShard(pickShard);
        Uninterruptibles.sleepUninterruptibly(3, TimeUnit.SECONDS);
        var releaseExpired = ReleaseExpiredSessions.newBuilder()
                .setInactivityPeriod(Durations.fromSeconds(1))
                .vBuild();
        ExpiredSessionsReleased result = sessionRegistry.releaseSessions(releaseExpired);
        assertThat(result.getShardCount())
                .isEqualTo(1);
        ExpiredSession expiredSession = result.getShard(0);
        assertThat(expiredSession.getShard())
                .isEqualTo(shard);
        assertThat(expiredSession.getPickedBy())
                .isEqualTo(worker);
    }

    @Test
    @DisplayName("release no expired sessions if does not match the criteria")
    @SuppressWarnings("ResultOfMethodCallIgnored")
    void releaseNoExpiresSessions() {
        var pickShard = PickUpShard.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .vBuild();
        sessionRegistry.pickShard(pickShard);
        var releaseExpired = ReleaseExpiredSessions.newBuilder()
                .setInactivityPeriod(Durations.fromSeconds(30))
                .vBuild();
        ExpiredSessionsReleased result = sessionRegistry.releaseSessions(releaseExpired);
        assertThat(result.getShardCount())
                .isEqualTo(0);
    }

    private static ManagedChannel localServer() {
        return ManagedChannelBuilder
                .forAddress(App.HOST, App.PORT)
                .usePlaintext()
                .build();
    }
}
