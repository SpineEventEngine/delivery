/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import com.google.common.annotations.VisibleForTesting;
import io.spine.client.Client;
import io.spine.environment.Production;
import io.spine.logging.Logging;
import io.spine.message.delivery.server.grpc.SessionRegistryService;
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

    /**
     * Creates a new instance of the application.
     */
    public App() {
    }

    /**
     * Initializes the application server environment and starts the gRPC container.
     */
    @VisibleForTesting
    public void initAndStart() {
        initEnv();
        var deliveryContext = DeliveryContext.newBuilder().build();
        this.internalGrpc =
                registerContext(GrpcContainer.inProcess(NAME), deliveryContext).build();
        var internalClient = Client.inProcess(NAME)
                                   .build();
        this.remoteGrpc =
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
        internalGrpc.addShutdownHook();
        try {
            internalGrpc.start();
            _info().log("Internal gRPC started as in-process server with name `%s`.", NAME);
        } catch (IOException e) {
            throw newIllegalStateException(
                    e, "Unable to start internal gRPC server with name `%s`.", NAME
            );
        }
        remoteGrpc.awaitTermination();
        internalGrpc.awaitTermination();
    }

    private static GrpcContainer.Builder
    registerContext(GrpcContainer.Builder container, DeliveryContext context) {
        return container
                .addService(context.commandService())
                .addService(context.queryService())
                .addService(context.subscriptionService());
    }

    /**
     * Returns the associated remote gRPC server container.
     */
    @VisibleForTesting
    public GrpcContainer remoteGrpc() {
        return remoteGrpc;
    }

    /**
     * Returns the associated internal gRPC server container.
     */
    @VisibleForTesting
    public GrpcContainer internalGrpc() {
        return internalGrpc;
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
