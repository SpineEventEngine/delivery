/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.demo;

import com.google.appengine.api.ThreadManager;
import com.google.common.flogger.FluentLogger;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.spine.client.Client;
import io.spine.core.TenantId;
import io.spine.logging.Logging;
import io.spine.delivery.DeliveryBootstrapper;
import io.spine.delivery.client.SimpleDeliveryClient;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.DeploymentType;
import io.spine.server.Server;
import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.Delivery;
import io.spine.server.delivery.DeliveryBuilder;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.ShardObserver;
import io.spine.server.delivery.ShardedWorkRegistry;
import io.spine.server.delivery.UniformAcrossAllShards;
import io.spine.server.storage.memory.InMemoryStorageFactory;
import io.spine.server.tenant.TenantAwareRunner;
import io.spine.server.transport.memory.InMemoryTransportFactory;

import javax.servlet.http.HttpServlet;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Suppliers.ofInstance;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * An abstract base servlet for the demo Application.
 *
 * <p>Starts and configures {@link GreeterContext demo} server and exposes a {@link Client} to the
 * server to inheritors.
 */
@SuppressWarnings("serial")
abstract class ContextAwareServlet extends HttpServlet implements Logging {

    private static final FluentLogger logger;

    /** The number of shards used for the signal delivery. **/
    private static final int NUMBER_OF_SHARDS = 50;

    @SuppressWarnings("unused")
    private static final String GCE_SERVER = "simple-message-delivery-server.c.spine-dev.internal";

    private static final ExecutorService limitedCachingExecutor = Executors.newFixedThreadPool(250);
    private static final ExecutorService observerExecutor = Executors.newFixedThreadPool(50);
    private static final ManagedChannel channel;

    protected static final Supplier<SimpleDeliveryClient> client;
    protected static final String SERVER_NAME = "DemoServer";
    protected static final Server server;
    protected static final Client spineClient;
    protected static final ShardedWorkRegistry workRegistry;

    static {
        useLog4j2FloggerBackend();
        channel = deliveryServerChannel();
        logger = Logging.loggerFor(ContextAwareServlet.class);
        workRegistry = configureEnv().orElseThrow(IllegalStateException::new);
        server = startServer();
        spineClient = inProcessClient();
        client = ofInstance(remoteDelivery());
    }

    private static SimpleDeliveryClient remoteDelivery() {
        return SimpleDeliveryClient.create(channel);
    }

    /**
     * Connects directly to a GCE instance.
     *
     * <p>For load-testing purposes only.
     */
    private static ManagedChannel deliveryServerChannel() {
        String server = "dns:///" + GCE_SERVER + ":8484";
        // Or use this one for local runs.
//        String server = "127.0.0.1:8484";
        return ManagedChannelBuilder
                .forTarget(server)
                .executor(limitedCachingExecutor)
                .usePlaintext()     // There is no SSL set up on GCE.
                .build();
    }

    private static Client inProcessClient() {
        ManagedChannel channel = InProcessChannelBuilder
                .forName(SERVER_NAME)
                .executor(limitedCachingExecutor)
                .build();
        return Client
                .usingChannel(channel)
                .build();
    }

    @SuppressWarnings("MagicNumber" /* Copied defaults from the `Executors.newCachedThreadPool`. */)
    private static ExecutorService parallelExecutor() {
        ThreadFactory threads = threadFactory();
        ExecutorService executor = new ThreadPoolExecutor(
                0,
                50,
                60L,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                threads
        );
        return executor;
    }

    /**
     * Configures and starts the {@link GreeterContext demo} server.
     */
    private static Server startServer() {
        BoundedContextBuilder demoContext = GreeterContext.builder();
        Server server = Server.inProcess(SERVER_NAME)
                              .add(demoContext)
                              .build();
        try {
            server.start();
        } catch (IOException e) {
            throw newIllegalStateException(e, "Unable to start Demo in-process server.");
        }
        return server;
    }

    /**
     * Configures the application {@link ServerEnvironment}.
     */
    private static Optional<ShardedWorkRegistry> configureEnv() {
        logger.atConfig()
              .log("Configuring `ServerEnvironment`.");
        DeliveryBuilder deliveryBuilder = DeliveryBootstrapper.newInstance()
                .withChannel(ofInstance(channel))
                .init();
        Delivery delivery = deliveryBuilder
                .setStrategy(UniformAcrossAllShards.forNumber(NUMBER_OF_SHARDS))
                .build();
        delivery.subscribe(new AsyncLocalObserver(observerExecutor));
        ServerEnvironment
                .when(Production.class)
                .use(delivery)
                .use(InMemoryTransportFactory.newInstance())
                .use(InMemoryStorageFactory.newInstance());
        return deliveryBuilder.workRegistry();
    }

    /**
     * Returns the thread factory suitable for the runtime environment.
     */
    private static ThreadFactory threadFactory() {
        ServerEnvironment env = ServerEnvironment.instance();
        DeploymentType deployment = env.deploymentType();
        ThreadFactory threads;
        if (deployment == DeploymentType.APPENGINE_CLOUD) {
            threads = ThreadManager.currentRequestThreadFactory();
        } else {
            threads = Executors.defaultThreadFactory();
        }
        return threads;
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

    /**
     * An asynchronous shard observer which runs on top of the {@linkplain #threadFactory()
     * runtime-specific thread factory}.
     */
    private static final class AsyncLocalObserver implements ShardObserver {

        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final ExecutorService executor;

        private AsyncLocalObserver(ExecutorService executor) {
            this.executor = checkNotNull(executor);
        }

        @SuppressWarnings("FutureReturnValueIgnored")
        @Override
        public void onMessage(InboxMessage update) {
            Delivery delivery = ServerEnvironment.instance()
                    .delivery();
            ShardIndex index = update.shardIndex();
            executor.submit(() -> runDelivery(update, delivery, index));
        }

        @SuppressWarnings("HandleMethodResult")
        private static void runDelivery(InboxMessage message, Delivery delivery, ShardIndex index) {
            TenantId tenant = message.tenant();
            TenantAwareRunner.with(tenant)
                             .run(() -> delivery.deliverMessagesFrom(index));
        }
    }
}
