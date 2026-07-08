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
import com.google.protobuf.Duration;
import com.google.protobuf.util.Durations;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.spine.logging.WithLogging;
import static java.lang.String.format;
import io.spine.delivery.server.grpc.AdminService;
import io.spine.delivery.server.grpc.HealthService;
import io.spine.delivery.server.grpc.InboxService;
import io.spine.delivery.server.grpc.ShardService;
import io.spine.delivery.server.grpc.UnableToCloseFactoryException;
import io.spine.server.storage.StorageFactory;
import io.spine.server.storage.hazelcast.HazelcastStorageFactory;
import io.spine.server.storage.memory.InMemoryStorageFactory;
import io.spine.server.storage.redis.RedisStorageFactory;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.util.Map;
import java.util.concurrent.ExecutorService;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.protobuf.util.Durations.checkPositive;
import static java.util.concurrent.Executors.newFixedThreadPool;

/**
 * Application exposing only an {@link InboxService} and {@link ShardService} instances via gRPC.
 */
public final class SimpleApp implements WithLogging {

    static {
        useLog4j2FloggerBackend();
    }

    private static final int DEFAULT_PORT = 8484;

    private static final int BYTES_IN_MB = 1_048_576;

    private static final int DEFAULT_MESSAGE_SIZE = 4 * BYTES_IN_MB; // 4 MiB

    /**
     * A default value for {@link #SHARD_PROCESSING_TIMEOUT} constant.
     *
     * <p>Specifying of {@linkplain Durations#ZERO zero duration} means that no restrictions
     * are imposed on shard processing time. Long-running sessions will NOT be considered stale.
     * They continue to be held until released by a worker itself.
     */
    private static final Duration NO_SHARD_PROCESSING_TIMEOUT = Durations.ZERO;

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

    /**
     * Maximum span of time, during which the session can be held
     * by a {@linkplain io.spine.server.delivery.WorkerId worker}.
     *
     * <p>Sometimes a worker fails to release a session. Such can happen due to
     * networking troubles, internal/application errors or an instance shutdown.
     *
     * <p>If a shard session is not released by the worker explicitly within
     * the specified timeout, it will be released automatically by Liquor.
     *
     * <p>By default, the stale-check is {@linkplain #NO_SHARD_PROCESSING_TIMEOUT turned off}.
     */
    private static final Duration SHARD_PROCESSING_TIMEOUT = shardProcessingTimeout();

    private static final ExecutorService executor = newFixedThreadPool(20);

    private @MonotonicNonNull Server server;
    private @MonotonicNonNull HealthService healthService;

    /**
     * The port at which the gRPC server is exposed.
     */
    private final int port;

    /**
     * Creates a new instance of the application exposed at the {@linkplain #PORT default port}.
     */
    public SimpleApp() {
        this(PORT);
    }

    /**
     * Creates a new instance of the application exposed at the given {@code port}.
     *
     * <p>Intended for tests, which bind an ephemeral port so that concurrently running
     * servers — such as the {@code server} module's app during a parallel build — do not
     * clash on a shared fixed port.
     */
    @VisibleForTesting
    SimpleApp(int port) {
        this.port = port;
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
        ReportingStorageFactory factory = storageFactory();
        InboxService inboxService = new InboxService(factory);
        ShardService shardService = new ShardService(factory, SHARD_PROCESSING_TIMEOUT);
        AdminService adminService = new AdminService(factory);
        healthService = new HealthService()
                .register(inboxService)
                .register(shardService)
                .register(adminService);
        this.server = ServerBuilder
                .forPort(port)
                .executor(executor)
                .addService(inboxService)
                .addService(shardService)
                .addService(adminService)
                .addService(healthService)
                .maxInboundMessageSize(MESSAGE_SIZE)
                .build();
        logger().atInfo().log(() -> format("Starting gRPC server..."));
        logger().atInfo().log(() -> format("Configured inbound message size: `%d` bytes.", MESSAGE_SIZE));
        Runtime runtime = Runtime.getRuntime();
        logger().atInfo().log(() -> format("Available memory %dMb.", runtime.maxMemory() / BYTES_IN_MB));
        logger().atInfo().log(() -> format("Configured shard processing timeout: `%d` seconds.",
                    SHARD_PROCESSING_TIMEOUT.getSeconds()));
        try {
            server.start();
            logger().atInfo().log(() -> format("gRPC server started at host '%s' and port '%d'.", HOST, port));
            server.awaitTermination();
        } catch (Exception e) {
            logger().atError().withCause(e)
                    .log(() -> format("Error running the gRPC server."));
        } finally {
            close(factory);
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

    /**
     * Closes the given {@code factory}.
     *
     * <p>Wraps the {@code close()} method into try / catch block and rethrows caught
     * {@code Exception} as {@code UnableToCloseStorageFactory}.
     */
    private static void close(StorageFactory factory) {
        try {
            factory.close();
        } catch (Exception e) {
            throw new UnableToCloseFactoryException(e);
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

    private static int messageSize() {
        @SuppressWarnings("CallToSystemGetenv")
        String size = System.getenv("MAX_INBOUND_MESSAGE_SIZE");
        if (isNullOrEmpty(size)) {
            return DEFAULT_MESSAGE_SIZE;
        }
        return Integer.parseInt(size);
    }

    private static Duration shardProcessingTimeout() {
        @SuppressWarnings("CallToSystemGetenv")
        String envVariable = System.getenv("SHARD_PROCESSING_TIMEOUT");
        if (isNullOrEmpty(envVariable)) {
            return NO_SHARD_PROCESSING_TIMEOUT;
        }
        var timeout = Integer.parseInt(envVariable);
        var duration = Durations.fromSeconds(timeout);
        return checkPositive(duration);
    }

    @SuppressWarnings("DuplicateStringLiteralInspection" /* Used in a different module. */)
    private ReportingStorageFactory storageFactory() {
        if (useRedis()) {
            logger().atConfig().log(() -> format("Using Redis storage."));
            return new ReportingStorageFactory(RedisStorageFactory.newInstance());
        }
        if (useHazelcast()) {
            logger().atConfig().log(() -> format("Using Hazelcast storage."));
            return new ReportingStorageFactory(HazelcastStorageFactory.newInstance());
        }
        logger().atConfig().log(() -> format("Using in-memory storage."));
        var factory = new SingletonStorageFactory(InMemoryStorageFactory.newInstance());
        return new ReportingStorageFactory(factory);
    }

    @SuppressWarnings("DuplicateStringLiteralInspection")
    private static boolean useRedis() {
        Map<String, String> envs = System.getenv();
        return envs.containsKey("USE_REDIS") && envs.containsKey("REDIS_HOST");
    }

    @SuppressWarnings("DuplicateStringLiteralInspection")
    private static boolean useHazelcast() {
        Map<String, String> envs = System.getenv();
        return envs.containsKey("USE_HAZELCAST");
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
