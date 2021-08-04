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
import io.spine.message.delivery.server.grpc.InboxService;
import io.spine.message.delivery.server.grpc.ShardService;
import io.spine.server.storage.StorageFactory;
import io.spine.server.storage.memory.InMemoryStorageFactory;
import io.spine.server.storage.redis.RedisStorageFactory;

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

    /**
     * A host to use for gRPC server.
     */
    private static final String HOST = "127.0.0.1";

    /**
     * A port to use for gRPC server.
     */
    @VisibleForTesting
    public static final int PORT = port();

    private static final ExecutorService executor = newFixedThreadPool(20);

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
        app.initAndStart();
    }

    @SuppressWarnings("OverlyBroadCatchBlock")
    private void initAndStart() {
        StorageFactory factory = storageFactory();
        InboxService inboxService = new InboxService(factory);
        ShardService shardService = new ShardService(factory);
        Server server =
                ServerBuilder.forPort(PORT)
                             .executor(executor)
                             .addService(inboxService)
                             .addService(shardService)
                             .build();
        _info().log("Starting gRPC server...");
        try {
            server.start();
            _info().log("gRPC server started at host '%s' and port '%d'.", HOST, PORT);
            server.awaitTermination();
        } catch (Exception e) {
            _error().withCause(e)
                    .log("Error running the gRPC server.");
        }
    }

    private static int port() {
        @SuppressWarnings("CallToSystemGetenv")
        String port = System.getenv("PORT");
        if (isNullOrEmpty(port)) {
            return DEFAULT_PORT;
        }
        return Integer.parseInt(port);
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
