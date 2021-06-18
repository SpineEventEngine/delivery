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

import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * Starts the {@code Delivery} gRPC server.
 */
public final class App implements Logging {

    /**
     * A host to use for gRPC server.
     */
    @VisibleForTesting
    static final String HOST = "127.0.0.1";

    /**
     * A port to use for gRPC server.
     */
    @VisibleForTesting
    static final int PORT = 8484;

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

    /**
     * Creates and starts a gRPC server and serves {@code Delivery} bounded context.
     */
    public static void main(String[] args) {
        var app = new App();
        app.initAndStart();
    }
}
