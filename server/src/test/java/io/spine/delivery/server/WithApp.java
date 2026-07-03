/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.server;

import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.spine.client.Client;
import io.spine.environment.Environment;
import io.spine.delivery.admin.given.BlockingMemoizingObserver;
import io.spine.delivery.admin.given.WithAckObserver;
import io.spine.delivery.admin.grpc.AdminServiceGrpc;
import io.spine.delivery.admin.grpc.AdminServiceGrpc.AdminServiceBlockingStub;
import io.spine.delivery.admin.grpc.AdminServiceGrpc.AdminServiceStub;
import io.spine.delivery.admin.grpc.ShardInfoUpdate;
import io.spine.delivery.grpc.ShardSessionRegistryServiceGrpc;
import io.spine.delivery.grpc.ShardSessionRegistryServiceGrpc.ShardSessionRegistryServiceBlockingStub;
import io.spine.server.ServerEnvironment;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;

/**
 * Abstract base for test classes that need a running {@link App}.
 */
public abstract class WithApp {

    private final App app = new App();

    private ShardSessionRegistryServiceBlockingStub sessionRegistry;

    private AdminServiceBlockingStub adminServiceBlocking;

    private AdminServiceStub adminService;

    private ManagedChannel channel;

    private Client client;

    @BeforeEach
    void startApp() {
        var appThread = new Thread(app::initAndStart);
        appThread.start();
        sleepUninterruptibly(Duration.ofSeconds(3)); // allow the server to start.
    }

    @BeforeEach
    void setupClients() {
        channel = localChannel();
        sessionRegistry = ShardSessionRegistryServiceGrpc.newBlockingStub(channel);
        adminServiceBlocking = AdminServiceGrpc.newBlockingStub(channel);
        adminService = AdminServiceGrpc.newStub(channel);
        client = Client.usingChannel(channel)
                       .build();
    }

    @AfterEach
    void shutdownApp() throws InterruptedException {
        channel.shutdownNow();
        if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
            throw new RuntimeException("Cannot animate the channel!");
        }
        app.shutdown();
    }

    @AfterAll
    static void resetEnvs() {
        Environment.instance()
                .reset();
        ServerEnvironment.instance()
                .reset();
    }

    /**
     * Creates new {@code ManagedChannel} connected to the server running locally.
     */
    protected static ManagedChannel localChannel() {
        return ManagedChannelBuilder
                .forAddress(App.HOST, App.PORT)
                .usePlaintext()
                .build();
    }

    /**
     * Returns the {@code ShardSessionRegistryServiceBlockingStub} connected to the local server.
     */
    protected ShardSessionRegistryServiceBlockingStub
    sessionRegistry() {
        return sessionRegistry;
    }

    /**
     * Returns the {@code AdminServiceBlockingStub} connected to the local server.
     */
    protected AdminServiceBlockingStub adminServiceBlocking() {
        return adminServiceBlocking;
    }

    /**
     * Returns the {@code AdminServiceStub} connected to the local server.
     */
    protected AdminServiceStub adminService() {
        return adminService;
    }

    /**
     * Returns a new {@code Client} connected to the local server.
     */
    public Client client() {
        return client;
    }

    /**
     * Subscribes to the shard updates on the {@code AdminService} and returns an observer that
     * collects all updates for further assertions.
     *
     * <p>Blocks the current thread and waits for the subscription to be acknowledged before
     * returning the observer.
     */
    protected BlockingMemoizingObserver<ShardInfoUpdate> subscribeToUpdates() {
        var observer = new BlockingMemoizingObserver<ShardInfoUpdate>();
        var ackObserver = new WithAckObserver(observer);
        adminService().subscribeToShardUpdates(Empty.getDefaultInstance(), ackObserver);
        ackObserver.waitForAcknowledgment();
        return observer;
    }
}
