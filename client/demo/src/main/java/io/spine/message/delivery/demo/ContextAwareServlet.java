/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.demo;

import com.google.appengine.api.ThreadManager;
import com.google.common.base.Suppliers;
import com.google.common.flogger.FluentLogger;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.spine.base.Production;
import io.spine.client.Client;
import io.spine.core.TenantId;
import io.spine.logging.Logging;
import io.spine.message.delivery.DeliveryBootstrapper;
import io.spine.message.delivery.client.DeliveryClient;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.DeploymentType;
import io.spine.server.Server;
import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.Delivery;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.ShardObserver;
import io.spine.server.delivery.UniformAcrossAllShards;
import io.spine.server.storage.memory.InMemoryStorageFactory;
import io.spine.server.tenant.TenantAwareRunner;
import io.spine.server.transport.memory.InMemoryTransportFactory;

import javax.servlet.http.HttpServlet;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

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
    private static final String GCE_SERVER = "message-delivery-server.c.spine-dev.internal";

    protected static final Supplier<DeliveryClient> client;
    protected static final String SERVER_NAME = "DemoServer";
    protected static final Server server;
    protected static final Client spineClient;

    static {
        useLog4j2FloggerBackend();
        logger = Logging.loggerFor(ContextAwareServlet.class);
        configureEnv();
        server = startServer();
        spineClient = Client
                .inProcess(SERVER_NAME)
                .build();
        client = Suppliers.ofInstance(remoteDelivery());
    }

    private static DeliveryClient remoteDelivery() {
        return DeliveryClient.create(deliveryServerChannel());
    }

    /**
     * Connects directly to a GCE instance.
     *
     * <p>For load-testing purposes only.
     */
    private static ManagedChannel deliveryServerChannel() {
        String server = "dns:///" + GCE_SERVER + ":8484";
        ThreadFactory threads = threadFactory();
        ExecutorService executor = Executors.newCachedThreadPool(threads);
        return ManagedChannelBuilder
                .forTarget(server)
                .executor(executor)
                .usePlaintext()     // There is no SSL set up on GCE.
                .build();
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
    private static void configureEnv() {
        logger.atConfig()
              .log("Configuring `ServerEnvironment`.");
        Delivery delivery = DeliveryBootstrapper.newInstance()
                .withChannel(deliveryServerChannel())
                .init()
                .setStrategy(UniformAcrossAllShards.forNumber(NUMBER_OF_SHARDS))
                .build();
        delivery.subscribe(new AsyncLocalObserver());
        ServerEnvironment
                .when(Production.class)
                .use(delivery)
                .use(InMemoryTransportFactory.newInstance())
                .use(InMemoryStorageFactory.newInstance());
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

        private final ExecutorService executor;

        private AsyncLocalObserver() {
            executor = Executors.newCachedThreadPool(threadFactory());
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
