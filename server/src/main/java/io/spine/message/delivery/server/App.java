/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import com.google.common.annotations.VisibleForTesting;
import io.spine.environment.Production;
import io.spine.logging.Logging;
import io.spine.server.GrpcContainer;
import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.Delivery;
import io.spine.server.storage.memory.InMemoryStorageFactory;
import io.spine.server.transport.memory.InMemoryTransportFactory;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.io.IOException;

import static com.google.common.base.Strings.isNullOrEmpty;
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
    static final String HOST = "127.0.0.1";

    /**
     * A port to use for gRPC server.
     */
    @VisibleForTesting
    static final int PORT = port();

    private @MonotonicNonNull DeliveryContext deliveryContext;
    private @MonotonicNonNull GrpcContainer grpc;

    /**
     * Creates a new instance of the application.
     */
    App() {
    }

    /**
     * Initializes the application server environment and starts the gRPC container.
     */
    @VisibleForTesting
    void initAndStart() {
        initEnv();
        this.deliveryContext = DeliveryContext.newBuilder().build();
        this.grpc = GrpcContainer
                .atPort(PORT)
                .addService(deliveryContext.commandService())
                .addService(deliveryContext.queryService())
                .addService(deliveryContext.subscriptionService())
                .build();
        grpc.addShutdownHook();
        try {
            grpc.start();
        } catch (IOException e) {
            throw newIllegalStateException(e, "Unable to start gRPC server at %s:%d.", HOST, PORT);
        }
        _info().log("gRPC server started at %s:%d", HOST, PORT);
        grpc.awaitTermination();
    }

    /**
     * Returns the configured {@code DeliveryContext}.
     */
    @VisibleForTesting
    DeliveryContext deliveryContext() {
        return deliveryContext;
    }

    /**
     * Returns the associated gRPC server container.
     */
    @VisibleForTesting
    GrpcContainer grpc() {
        return grpc;
    }

    private static void initEnv() {
        ServerEnvironment
                .when(Production.class)
                .use(InMemoryStorageFactory.newInstance())
                .use(InMemoryTransportFactory.newInstance())
                .use(Delivery.localAsync());
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
     * Creates and starts a gRPC server and serves {@code Delivery} bounded context.
     */
    public static void main(String[] args) {
        var app = new App();
        app.initAndStart();
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
