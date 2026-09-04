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

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.Duration;
import com.google.protobuf.util.Durations;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.spine.delivery.server.grpc.AdminService;
import io.spine.delivery.server.grpc.HealthService;
import io.spine.delivery.server.grpc.InboxService;
import io.spine.delivery.server.grpc.ShardService;
import io.spine.logging.WithLogging;
import io.spine.server.storage.hazelcast.HazelcastStorageFactory;
import io.spine.server.storage.memory.InMemoryStorageFactory;
import io.spine.server.storage.redis.RedisStorageFactory;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.protobuf.util.Durations.checkPositive;
import static java.lang.String.format;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Application exposing only {@link InboxService} and {@link ShardService} instances via gRPC.
 */
public final class DeliveryServerApp implements WithLogging {

    private static final int DEFAULT_PORT = 8484;

    private static final int BYTES_IN_MB = 1_048_576;

    /**
     * How long {@link #awaitPort()} waits for the gRPC server to start.
     */
    private static final int STARTUP_TIMEOUT_SECONDS = 10;

    /**
     * How long {@link #shutdown()} waits for the gRPC server to terminate gracefully
     * before forcing the termination.
     */
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 5;

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
     * A host to use for a gRPC server.
     */
    @VisibleForTesting
    public static final String HOST = "127.0.0.1";

    /**
     * A port to use for a gRPC server.
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
     * the specified timeout, it will be released automatically by the Delivery server.
     *
     * <p>By default, the stale-check is {@linkplain #NO_SHARD_PROCESSING_TIMEOUT turned off}.
     */
    private static final Duration SHARD_PROCESSING_TIMEOUT = shardProcessingTimeout();

    private static final ExecutorService executor = newFixedThreadPool(20);

    private @MonotonicNonNull Server server;
    private @MonotonicNonNull HealthService healthService;

    /**
     * The port at which the gRPC server is exposed.
     *
     * <p>Zero asks the operating system to assign a free port when the server starts.
     * The assigned port is then available via {@link #awaitPort()}.
     */
    private final int port;

    /**
     * Completes with the port the gRPC server listens on once it has started, or
     * completes exceptionally if the application fails to start.
     */
    private final CompletableFuture<Integer> boundPort = new CompletableFuture<>();

    /**
     * Creates a new instance of the application exposed at the {@linkplain #PORT default port}.
     */
    public DeliveryServerApp() {
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
    DeliveryServerApp(int port) {
        this.port = port;
    }

    /**
     * Creates and starts a gRPC server and serves the {@code Delivery} bounded context.
     */
    public static void main(String[] args) {
        var app = new DeliveryServerApp();
        Runtime.getRuntime()
               .addShutdownHook(new Thread(app::shutdown));
        app.initAndStart();
    }

    @VisibleForTesting
    void initAndStart() {
        try {
            runServer();
        } catch (IOException | InterruptedException e) {
            boundPort.completeExceptionally(e);
            logger().atError().withCause(e)
                    .log(() -> "Error running the gRPC server.");
        }
    }

    /**
     * Creates the storage, starts the gRPC server, and blocks until the server terminates.
     *
     * <p>Completes {@link #boundPort} with the port the started server listens on. Failures
     * are left to {@link #initAndStart()}, which records them in the same future, so that
     * a caller waiting for the port learns the cause instead of waiting for the timeout.
     */
    private void runServer() throws IOException, InterruptedException {
        var factory = storageFactory();
        var inboxService = new InboxService(factory);
        var shardService = new ShardService(factory, SHARD_PROCESSING_TIMEOUT);
        var adminService = new AdminService(factory);
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
        logger().atInfo()
                .log(() -> "Starting gRPC server...");
        logger().atInfo()
                .log(() -> format("Configured inbound message size: `%d` bytes.", MESSAGE_SIZE));
        var runtime = Runtime.getRuntime();
        logger().atInfo()
                .log(() -> format("Available memory %dMb.", runtime.maxMemory() / BYTES_IN_MB));
        logger().atInfo()
                .log(() -> format("Configured shard processing timeout: `%d` seconds.",
                    SHARD_PROCESSING_TIMEOUT.getSeconds()));
        try {
            server.start();
            var assignedPort = server.getPort();
            boundPort.complete(assignedPort);
            logger().atInfo().log(() -> format(
                    "gRPC server started at host '%s' and port '%d'.", HOST, assignedPort));
            server.awaitTermination();
        } finally {
            factory.close();
        }
    }

    /**
     * Waits for the gRPC server to start, and returns the port it listens on.
     *
     * <p>Tests construct the app with port zero and read the port back here, instead of
     * picking a free port in advance: the operating system assigns the port while binding
     * it, so no other process can take it in between.
     *
     * @throws IllegalStateException
     *         if the application fails to start, with the original failure as the cause, or
     *         if it does not start within {@link #STARTUP_TIMEOUT_SECONDS} seconds
     * @throws InterruptedException
     *         if the current thread is interrupted while waiting
     */
    @VisibleForTesting
    int awaitPort() throws InterruptedException {
        return awaitPort(STARTUP_TIMEOUT_SECONDS, SECONDS);
    }

    /**
     * Same as {@link #awaitPort()}, but waiting for the given time.
     *
     * <p>Allows a test to exercise the timeout without waiting for
     * {@link #STARTUP_TIMEOUT_SECONDS} seconds.
     */
    @VisibleForTesting
    @SuppressWarnings("ThrowInsideCatchBlockWhichIgnoresCaughtException" /*
        We get the original exception as the cause, so it is not ignored.
    */)
    int awaitPort(long timeout, TimeUnit unit) throws InterruptedException {
        try {
            return boundPort.get(timeout, unit);
        } catch (ExecutionException e) {
            throw new IllegalStateException("The gRPC server failed to start.", e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException(
                    format("The gRPC server has not started within %d %s.",
                           timeout, unit.name().toLowerCase(Locale.ROOT)), e);
        }
    }

    /**
     * Shuts down the application and awaits the termination of the gRPC server.
     *
     * <p>Awaiting matters both in production — the method runs as a JVM shutdown hook,
     * so returning early would let the JVM die while connections are still served — and
     * in tests, where a server still releasing its resources after this method returns
     * may race the connections of the next started test.
     *
     * <p>If the server does not terminate within {@link #SHUTDOWN_TIMEOUT_SECONDS},
     * or the current thread is interrupted, the termination is forced.
     */
    @VisibleForTesting
    public void shutdown() {
        if (healthService != null) {
            healthService.markNonHealthy();
        }
        if (server != null) {
            server.shutdown();
            awaitTermination();
        }
    }

    /**
     * Waits until the {@link #server} terminates, forcing the termination on timeout
     * or interruption.
     */
    private void awaitTermination() {
        try {
            var terminated = server.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, SECONDS);
            if (!terminated) {
                // A forceful shutdown is asynchronous too, so it is awaited as well.
                // Otherwise this method could return while `runServer()` is still
                // blocked and its storage factory still open.
                server.shutdownNow();
                server.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, SECONDS);
            }
        } catch (InterruptedException e) {
            server.shutdownNow();
            Thread.currentThread()
                  .interrupt();
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
        var port = System.getenv("PORT");
        if (isNullOrEmpty(port)) {
            return DEFAULT_PORT;
        }
        return Integer.parseInt(port);
    }

    private static int messageSize() {
        @SuppressWarnings("CallToSystemGetenv")
        var size = System.getenv("MAX_INBOUND_MESSAGE_SIZE");
        if (isNullOrEmpty(size)) {
            return DEFAULT_MESSAGE_SIZE;
        }
        return Integer.parseInt(size);
    }

    private static Duration shardProcessingTimeout() {
        @SuppressWarnings("CallToSystemGetenv")
        var envVariable = System.getenv("SHARD_PROCESSING_TIMEOUT");
        if (isNullOrEmpty(envVariable)) {
            return NO_SHARD_PROCESSING_TIMEOUT;
        }
        var timeout = Integer.parseInt(envVariable);
        var duration = Durations.fromSeconds(timeout);
        return checkPositive(duration);
    }

    private ReportingStorageFactory storageFactory() {
        if (useRedis()) {
            logger().atConfig().log(() -> "Using Redis storage.");
            return new ReportingStorageFactory(RedisStorageFactory.newInstance());
        }
        if (useHazelcast()) {
            logger().atConfig().log(() -> "Using Hazelcast storage.");
            return new ReportingStorageFactory(HazelcastStorageFactory.newInstance());
        }
        logger().atConfig().log(() -> "Using in-memory storage.");
        var factory = new SingletonStorageFactory(InMemoryStorageFactory.newInstance());
        return new ReportingStorageFactory(factory);
    }

    @SuppressWarnings("DuplicateStringLiteralInspection")
    private static boolean useRedis() {
        var envs = System.getenv();
        return envs.containsKey("USE_REDIS") && envs.containsKey("REDIS_HOST");
    }

    @SuppressWarnings("DuplicateStringLiteralInspection")
    private static boolean useHazelcast() {
        var envs = System.getenv();
        return envs.containsKey("USE_HAZELCAST");
    }
}
