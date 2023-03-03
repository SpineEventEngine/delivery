/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.spine.message.delivery.admin.grpc.AdminServiceGrpc;
import io.spine.message.delivery.admin.grpc.AdminServiceGrpc.AdminServiceBlockingStub;
import io.spine.message.delivery.admin.grpc.AdminServiceGrpc.AdminServiceStub;
import io.spine.message.delivery.grpc.InboxServiceGrpc;
import io.spine.message.delivery.grpc.ShardServiceGrpc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.time.Duration;

import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;

/**
 * An abstract base for tests which rely on the running {@linkplain SimpleApp app}.
 */
public abstract class WithApp {

    private final SimpleApp app = new SimpleApp();

    private AdminServiceBlockingStub adminServiceBlocking;

    private AdminServiceStub adminService;

    private ManagedChannel serverChannel;

    @BeforeEach
    void startApp() {
        var appThread = new Thread(app::initAndStart);
        appThread.start();
        sleepUninterruptibly(Duration.ofSeconds(1)); // allow the server to start.
        serverChannel = newServerChannel();
        adminServiceBlocking = AdminServiceGrpc.newBlockingStub(serverChannel);
        adminService = AdminServiceGrpc.newStub(serverChannel);
    }

    @AfterEach
    void shutdownApp() {
        app.shutdown();
    }

    /**
     * Returns a channel connected to the running application.
     */
    protected final ManagedChannel serverChannel() {
        return serverChannel;
    }

    /**
     * Returns the running application instance.
     */
    protected final SimpleApp app() {
        return app;
    }

    /**
     * Returns blocking {@code ShardService} connected to the app.
     */
    protected final ShardServiceGrpc.ShardServiceBlockingStub syncShardService() {
        return ShardServiceGrpc.newBlockingStub(serverChannel());
    }

    /**
     * Returns blocking {@code AdminService} connected to the app.
     */
    protected final AdminServiceBlockingStub syncAdminService() {
        return adminServiceBlocking;
    }

    /**
     * Gets the {@code AdminServiceStub} connected to the local server.
     */
    protected AdminServiceStub adminService() {
        return adminService;
    }

    /**
     * Returns blocking {@code InboxService} connected to the app.
     */
    protected final InboxServiceGrpc.InboxServiceBlockingStub syncInboxService() {
        return InboxServiceGrpc.newBlockingStub(serverChannel());
    }

    /**
     * Returns a channel connected to the running application.
     */
    private static ManagedChannel newServerChannel() {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(SimpleApp.HOST, SimpleApp.PORT)
                .usePlaintext()
                .build();
        return channel;
    }
}
