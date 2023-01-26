/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.spine.environment.Environment;
import io.spine.message.delivery.admin.grpc.AdminServiceGrpc;
import io.spine.message.delivery.admin.grpc.AdminServiceGrpc.AdminServiceBlockingStub;
import io.spine.message.delivery.grpc.ShardSessionRegistryServiceGrpc;
import io.spine.message.delivery.grpc.ShardSessionRegistryServiceGrpc.ShardSessionRegistryServiceBlockingStub;
import io.spine.server.ServerEnvironment;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.time.Duration;

import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;

/**
 * Abstract base for test classes that need a running {@link App}.
 */
public abstract class WithApp extends DeliveryTest {

    private final App app = new App();

    private ShardSessionRegistryServiceBlockingStub sessionRegistry;

    private AdminServiceBlockingStub adminService;

    @BeforeEach
    void startApp() {
        var appThread = new Thread(app::initAndStart);
        appThread.start();
        sleepUninterruptibly(Duration.ofSeconds(3)); // allow the server to start.
    }

    @BeforeEach
    void setupClients() {
        sessionRegistry = ShardSessionRegistryServiceGrpc.newBlockingStub(localChannel());
        adminService = AdminServiceGrpc.newBlockingStub(localChannel());
    }

    @AfterEach
    void shutdownApp() {
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
     * Gets the {@code ShardSessionRegistryServiceBlockingStub} connected to the local server.
     */
    protected ShardSessionRegistryServiceBlockingStub
    sessionRegistry() {
        return sessionRegistry;
    }

    /**
     * Gets the {@code AdminServiceBlockingStub} connected to the local server.
     */
    protected AdminServiceBlockingStub adminService() {
        return adminService;
    }
}
