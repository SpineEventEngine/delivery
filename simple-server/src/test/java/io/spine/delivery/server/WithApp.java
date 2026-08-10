/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.server;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.spine.base.Time;
import io.spine.delivery.InboxServiceGrpc;
import io.spine.delivery.ShardServiceGrpc;
import io.spine.delivery.admin.grpc.AdminServiceGrpc;
import io.spine.delivery.admin.grpc.AdminServiceGrpc.AdminServiceBlockingStub;
import io.spine.delivery.admin.grpc.AdminServiceGrpc.AdminServiceStub;
import io.spine.delivery.server.event.TestEvent;
import io.spine.type.KnownTypes;
import io.spine.type.TypeUrl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkState;

/**
 * An abstract base for tests which rely on the running {@linkplain SimpleApp app}.
 */
public abstract class WithApp {

    /**
     * The app under test, bound to an ephemeral port.
     *
     * <p>Port zero makes the operating system assign a free port while the server binds it,
     * so that concurrently running servers — such as another module's app during a parallel
     * build — never clash, and no other process can take the port in between.
     */
    private final SimpleApp app = new SimpleApp(0);

    /**
     * The port the {@linkplain #app app} listens on, known only once it has started.
     */
    private int port;

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
    void startApp() throws InterruptedException {
        var appThread = new Thread(app::initAndStart);
        appThread.start();
        port = app.awaitPort();
        serverChannel = newServerChannel();
        adminServiceBlocking = AdminServiceGrpc.newBlockingStub(serverChannel);
        adminService = AdminServiceGrpc.newStub(serverChannel);
    }

    /**
     * Shuts down the client channel and then the application.
     *
     * <p>The channel is a client-side object with a lifecycle of its own: shutting down
     * the server does not close it. It is closed first — before the server — so that it
     * does not attempt to reconnect to an already stopped server.
     */
    @AfterEach
    void shutdownApp() throws InterruptedException {
        serverChannel.shutdownNow();
        serverChannel.awaitTermination(5, TimeUnit.SECONDS);
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
        var channel = ManagedChannelBuilder
                .forAddress(SimpleApp.HOST, port)
                .usePlaintext()
                .build();
        return channel;
    }
}
