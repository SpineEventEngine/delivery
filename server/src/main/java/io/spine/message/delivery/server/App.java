/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import com.google.common.annotations.VisibleForTesting;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.spine.client.Client;
import io.spine.environment.Production;
import io.spine.logging.Logging;
import io.spine.message.delivery.server.grpc.SessionRegistryService;
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
public final class App implements Logging {

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
     * Creates a new instance of the application.
     */
    public App() {
    }

    /**
     * Creates and starts a gRPC server and serves {@code Delivery} bounded context.
     */
    public static void main(String[] args) {
        var app = new App();
        app.initAndStart();
    }

    /**
     * Initializes the application server environment and starts the gRPC container.
     */
    @VisibleForTesting
    public void initAndStart() {
        initEnv();
        this.internalClient = memoize(App::internalClient);
        var deliveryContext = DeliveryContext.newBuilder()
                .contextClient(internalClient)
                .build();
        this.internalGrpc = startInternalGrpc(deliveryContext);
        this.remoteGrpc = startRemoteGrpc(deliveryContext, internalClient);

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
        var remoteGrpc =
                registerContext(GrpcContainer.atPort(PORT), deliveryContext)
                        .addService(new SessionRegistryService(internalClient))
                        .build();
        remoteGrpc.addShutdownHook();
        try {
            remoteGrpc.start();
            _info().log("Remote gRPC server started at port `%d`.", PORT);
        } catch (IOException e) {
            throw newIllegalStateException(
                    e, "Unable to start remote gRPC server at port `%d`.", PORT
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
            _info().log("Internal gRPC started as in-process server with name `%s`.", NAME);
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

    private static void initEnv() {
        ServerEnvironment
                .when(Production.class)
                .useStorageFactory(env -> {
                    if (useRedis()) {
                        return RedisStorageFactory.newInstance();
                    }
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
