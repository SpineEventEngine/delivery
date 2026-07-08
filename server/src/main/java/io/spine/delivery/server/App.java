/*
 * Copyright 2026, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.delivery.server;

import com.google.common.annotations.VisibleForTesting;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.spine.client.Client;
import io.spine.environment.DefaultMode;
import io.spine.logging.WithLogging;
import static java.lang.String.format;
import io.spine.delivery.server.grpc.AdminService;
import io.spine.delivery.server.grpc.SessionRegistryService;
import io.spine.server.GrpcContainer;
import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.Delivery;
import io.spine.server.storage.memory.InMemoryStorageFactory;
import io.spine.server.storage.redis.RedisStorageFactory;
import io.spine.server.transport.memory.InMemoryTransportFactory;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Suppliers.memoize;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * Starts the {@code Delivery} gRPC server.
 */
public final class App implements WithLogging {

    static {
        useLog4j2FloggerBackend();
    }

    private static final int DEFAULT_PORT = 8484;

    /**
     * A host to use for gRPC server.
     */
    @VisibleForTesting
    public static final String HOST = "127.0.0.1";

    /**
     * A port to use for gRPC server.
     */
    @VisibleForTesting
    public static final int PORT = port();

    /**
     * The name of the internal gRPC app container.
     */
    private static final String NAME = "Delivery App";

    private @MonotonicNonNull GrpcContainer internalGrpc;
    private @MonotonicNonNull GrpcContainer remoteGrpc;
    private @MonotonicNonNull Supplier<Client> internalClient;

    /**
     * The port at which the remote gRPC server is exposed.
     */
    private final int port;

    /**
     * Creates a new instance of the application exposed at the {@linkplain #PORT default port}.
     */
    public App() {
        this(PORT);
    }

    /**
     * Creates a new instance of the application exposed at the given {@code port}.
     *
     * <p>Intended for tests, which bind an ephemeral port so that concurrently running
     * servers — such as the {@code simple-server} app during a parallel build — do not
     * clash on a shared fixed port.
     */
    @VisibleForTesting
    App(int port) {
        this.port = port;
    }

    /**
     * Creates and starts a gRPC server and serves {@code Delivery} bounded context.
     */
    public static void main(String[] args) {
        var app = new App();
        app.initAndStart();
    }

    /**
     * Initializes the environment, starts the gRPC containers, and blocks until they terminate.
     *
     * <p>This is the entry point for {@link #main(String[])}. Tests should instead call
     * {@link #start()} — which returns as soon as the servers are accepting requests — followed
     * by {@link #shutdown()}, so that startup completes synchronously and cleanup is never
     * skipped.
     */
    public void initAndStart() {
        start();
        awaitTermination();
    }

    /**
     * Initializes the application server environment and starts the gRPC containers, returning
     * once both are accepting requests.
     */
    @VisibleForTesting
    void start() {
        initEnv();
        this.internalClient = memoize(App::internalClient);
        var deliveryContext = DeliveryContext.newBuilder()
                .contextClient(internalClient)
                .build();
        this.internalGrpc = startInternalGrpc(deliveryContext);
        this.remoteGrpc = startRemoteGrpc(deliveryContext, internalClient);
    }

    /**
     * Blocks the calling thread until both gRPC containers terminate.
     */
    private void awaitTermination() {
        internalGrpc.awaitTermination();
        remoteGrpc.awaitTermination();
    }

    private static Client internalClient() {
        var channel = InProcessChannelBuilder
                .forName(NAME)
                .executor(parallelExecutor())
                .build();
        return Client
                .usingChannel(channel)
                .build();
    }

    private static ExecutorService parallelExecutor() {
        ThreadFactory threads = Executors.defaultThreadFactory();
        ExecutorService executor = Executors.newCachedThreadPool(threads);
        return executor;
    }

    private GrpcContainer
    startRemoteGrpc(DeliveryContext deliveryContext, Supplier<Client> internalClient) {
        Client client = internalClient.get();
        var remoteGrpc =
                registerContext(GrpcContainer.atPort(port), deliveryContext)
                        .addService(new SessionRegistryService(client))
                        .addService(new AdminService(client))
                        .build();
        remoteGrpc.addShutdownHook();
        try {
            remoteGrpc.start();
            logger().atInfo().log(() -> format("Remote gRPC server started at port `%d`.", port));
        } catch (IOException e) {
            throw newIllegalStateException(
                    e, "Unable to start remote gRPC server at port `%d`.", port
            );
        }
        return remoteGrpc;
    }

    private GrpcContainer startInternalGrpc(DeliveryContext deliveryContext) {
        var internalGrpc =
                registerContext(GrpcContainer.inProcess(NAME), deliveryContext).build();
        internalGrpc.addShutdownHook();
        try {
            internalGrpc.start();
            logger().atInfo().log(() -> format("Internal gRPC started as in-process server with name `%s`.", NAME));
        } catch (IOException e) {
            throw newIllegalStateException(
                    e, "Unable to start internal gRPC server with name `%s`.", NAME
            );
        }
        return internalGrpc;
    }

    private static GrpcContainer.Builder
    registerContext(GrpcContainer.Builder container, DeliveryContext context) {
        return container
                .addService(context.commandService())
                .addService(context.queryService())
                .addService(context.subscriptionService());
    }

    /**
     * Shuts down the application.
     */
    @VisibleForTesting
    public void shutdown() {
        if (internalClient != null) {
            internalClient.get()
                          .shutdown();
        }
        if (internalGrpc != null) {
            internalGrpc.shutdownNowAndWait();
        }
        if (remoteGrpc != null) {
            remoteGrpc.shutdownNowAndWait();
        }
    }

    @SuppressWarnings("DuplicateStringLiteralInspection" /* Used in different module. */)
    private void initEnv() {
        ServerEnvironment
                .when(DefaultMode.class)
                .useStorageFactory(env -> {
                    if (useRedis()) {
                        logger().atConfig().log(() -> format("Using Redis storage."));
                        return RedisStorageFactory.newInstance();
                    }
                    logger().atConfig().log(() -> format("Using in-memory storage."));
                    return InMemoryStorageFactory.newInstance();
                })
                .use(InMemoryTransportFactory.newInstance())
                .use(Delivery.localAsync());
    }

    @SuppressWarnings("DuplicateStringLiteralInspection")
    private static boolean useRedis() {
        Map<String, String> envs = System.getenv();
        return envs.containsKey("USE_REDIS") && envs.containsKey("REDIS_HOST");
    }

    private static int port() {
        @SuppressWarnings("CallToSystemGetenv")
        String port = System.getenv("PORT");
        if (isNullOrEmpty(port)) {
            return DEFAULT_PORT;
        }
        return Integer.parseInt(port);
    }

    /**
     * Configures Log4j2 as the <a href="https://github.com/google/flogger">Flogger</a> backend.
     */
    @SuppressWarnings({
            "DuplicateStringLiteralInspection", /* Used in non-related context. */
            "AccessOfSystemProperties" /* There is no better way to configure Flogger. */
    })
    private static void useLog4j2FloggerBackend() {
        System.setProperty(
                "flogger.backend_factory",
                "com.google.common.flogger.backend.log4j2.Log4j2BackendFactory#getInstance"
        );
    }
}
