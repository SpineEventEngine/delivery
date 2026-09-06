/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
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
 * An abstract base for tests that rely on the running {@linkplain DeliveryServerApp app}.
 */
public abstract class WithApp {

    /**
     * The app under test, bound to an ephemeral port.
     *
     * <p>Port zero makes the operating system assign a free port while the server binds it,
     * so that concurrently running servers — such as another module's app during a parallel
     * build — never clash, and no other process can take the port in between.
     */
    private final DeliveryServerApp app = new DeliveryServerApp(0);

    /**
     * The port the {@link #app} listens on, known only once it has started.
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
     *
     * <p>The shutdown is graceful: by this point the test has cancelled its streaming
     * calls (see e.g. {@code AdminServiceTest.cancelSubscriptions()}), so the channel
     * terminates cleanly. Forceful termination remains only as a timeout fallback.
     * The app then awaits the termination of its server, so that no released-resource
     * races are left behind for the next test.
     */
    @AfterEach
    void shutdownApp() throws InterruptedException {
        serverChannel.shutdown();
        if (!serverChannel.awaitTermination(5, TimeUnit.SECONDS)) {
            serverChannel.shutdownNow();
            serverChannel.awaitTermination(5, TimeUnit.SECONDS);
        }
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
    protected final DeliveryServerApp app() {
        return app;
    }

    /**
     * Returns the blocking {@code ShardService} connected to the app.
     */
    protected final ShardServiceGrpc.ShardServiceBlockingStub syncShardService() {
        return ShardServiceGrpc.newBlockingStub(serverChannel());
    }

    /**
     * Returns the blocking {@code AdminService} connected to the app.
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
     * Returns the blocking {@code InboxService} connected to the app.
     */
    protected final InboxServiceGrpc.InboxServiceBlockingStub syncInboxService() {
        return InboxServiceGrpc.newBlockingStub(serverChannel());
    }

    /**
     * Returns a channel connected to the running application.
     */
    private ManagedChannel newServerChannel() {
        var channel = ManagedChannelBuilder
                .forAddress(DeliveryServerApp.HOST, port)
                .usePlaintext()
                .build();
        return channel;
    }
}
