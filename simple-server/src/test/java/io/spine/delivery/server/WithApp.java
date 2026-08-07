/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.server;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.spine.base.Time;
import io.spine.delivery.admin.grpc.AdminServiceGrpc;
import io.spine.delivery.admin.grpc.AdminServiceGrpc.AdminServiceBlockingStub;
import io.spine.delivery.admin.grpc.AdminServiceGrpc.AdminServiceStub;
import io.spine.delivery.InboxServiceGrpc;
import io.spine.delivery.server.event.TestEvent;
import io.spine.delivery.ShardServiceGrpc;
import io.spine.type.KnownTypes;
import io.spine.type.TypeUrl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;

/**
 * An abstract base for tests which rely on the running {@linkplain SimpleApp app}.
 */
public abstract class WithApp {

    /**
     * A free port picked per test instance, so that concurrently running servers — such as the
     * {@code server} module's app during a parallel build — never clash on a shared fixed port.
     */
    private final int port = freePort();

    private final SimpleApp app = new SimpleApp(port);

    private AdminServiceBlockingStub adminServiceBlocking;

    private AdminServiceStub adminService;

    private ManagedChannel serverChannel;

    /**
     * Eagerly initializes Spine's {@link KnownTypes} registry on a single thread before any
     * test runs.
     *
     * <p>{@link #startApp()} launches the gRPC server on a background thread. Without this
     * warm-up, that thread and the test thread can first touch the lazily-initialized type
     * registry concurrently; under CPU pressure (e.g. a parallel CI build) the test thread
     * may then observe the registry incompletely and fail to resolve a Protobuf type such as
     * {@code spine.delivery.TestEvent}, throwing {@link io.spine.type.UnknownTypeException}.
     * Resolving the type once here — before any server thread exists — removes that race.
     */
    @BeforeAll
    static void warmUpTypeRegistry() {
        var testEventType = TypeUrl.of(TestEvent.getDefaultInstance());
        checkState(KnownTypes.instance().contains(testEventType),
                   "Expected `%s` to be a known type before the tests run.",
                   testEventType.value());
    }

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

    @AfterEach
    void resetTimeProvider() {
        Time.resetProvider();
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
     * Returns the {@code AdminServiceStub} connected to the local server.
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
    private ManagedChannel newServerChannel() {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(SimpleApp.HOST, port)
                .usePlaintext()
                .build();
        return channel;
    }

    /**
     * Reserves a free ephemeral port from the operating system.
     */
    private static int freePort() {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to reserve a free port for the test gRPC server.", e);
        }
    }
}
