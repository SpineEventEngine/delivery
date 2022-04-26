/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import com.google.common.annotations.VisibleForTesting;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.spine.logging.Logging;
import io.spine.message.delivery.server.grpc.HealthService;
import io.spine.message.delivery.server.grpc.InboxService;
import io.spine.message.delivery.server.grpc.ShardService;
import io.spine.server.storage.StorageFactory;
import io.spine.server.storage.memory.InMemoryStorageFactory;
import io.spine.server.storage.redis.RedisStorageFactory;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.util.Map;
import java.util.concurrent.ExecutorService;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.concurrent.Executors.newFixedThreadPool;

/**
 * Application exposing only an {@link InboxService} and {@link ShardService} instances via gRPC.
 */
public final class SimpleApp implements Logging {

    static {
        useLog4j2FloggerBackend();
    }

    private static final int DEFAULT_PORT = 8484;

    private static final int BYTES_IN_MB = 1_048_576;

    private static final int DEFAULT_MESSAGE_SIZE = 4 * BYTES_IN_MB; // 4 MiB

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
     * A max size of the inbound payload that can be received through the gRPC channel.
     */
    private static final int MESSAGE_SIZE = messageSize();

    private static final ExecutorService executor = newFixedThreadPool(20);

    private @MonotonicNonNull Server server;
    private @MonotonicNonNull HealthService healthService;

    /**
     * Creates a new instance of the application.
     */
    public SimpleApp() {
    }

    /**
     * Creates and starts a gRPC server and serves {@code Delivery} bounded context.
     */
    public static void main(String[] args) {
        var app = new SimpleApp();
        Runtime.getRuntime()
               .addShutdownHook(new Thread(app::shutdown));
        app.initAndStart();
    }

    @VisibleForTesting
    @SuppressWarnings("OverlyBroadCatchBlock" /* We do want to catch all exceptions. */)
    void initAndStart() {
        StorageFactory factory = storageFactory();
        InboxService inboxService = new InboxService(factory);
        ShardService shardService = new ShardService(factory);
        healthService = new HealthService()
                .register(inboxService)
                .register(shardService);
        this.server = ServerBuilder
                .forPort(PORT)
                .executor(executor)
                .addService(inboxService)
                .addService(shardService)
                .addService(healthService)
                .maxInboundMessageSize(MESSAGE_SIZE)
                .build();
        _info().log("Starting gRPC server...");
        _info().log("Configured inbound message size: `%d` bytes.", MESSAGE_SIZE);
        Runtime runtime = Runtime.getRuntime();
        _info().log("Available memory %dMb", runtime.maxMemory() / BYTES_IN_MB);
        try {
            server.start();
            _info().log("gRPC server started at host '%s' and port '%d'.", HOST, PORT);
            server.awaitTermination();
        } catch (Exception e) {
            _error().withCause(e)
                    .log("Error running the gRPC server.");
        }
    }

    /**
     * Shuts down the application.
     */
    @VisibleForTesting
    public void shutdown() {
        if (healthService != null) {
            healthService.markNonHealthy();
        }
        if (server != null) {
            server.shutdown();
        }
    }

    /**
     * Returns the configured health check service.
     */
    @VisibleForTesting
    public HealthService healthService() {
        return healthService;
    }

    private static int port() {
        @SuppressWarnings("CallToSystemGetenv")
        String port = System.getenv("PORT");
        if (isNullOrEmpty(port)) {
            return DEFAULT_PORT;
        }
        return Integer.parseInt(port);
    }

    private static int messageSize() {
        @SuppressWarnings("CallToSystemGetenv")
        String size = System.getenv("MAX_INBOUND_MESSAGE_SIZE");
        if (isNullOrEmpty(size)) {
            return DEFAULT_MESSAGE_SIZE;
        }
        return Integer.parseInt(size);
    }

    @SuppressWarnings("DuplicateStringLiteralInspection" /* Used in different module. */)
    private StorageFactory storageFactory() {
        if (useRedis()) {
            _config().log("Using Redis storage.");
            return RedisStorageFactory.newInstance();
        }
        _config().log("Using in-memory storage.");
        return InMemoryStorageFactory.newInstance();
    }

    @SuppressWarnings("DuplicateStringLiteralInspection")
    private static boolean useRedis() {
        Map<String, String> envs = System.getenv();
        return envs.containsKey("USE_REDIS") && envs.containsKey("REDIS_HOST");
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
