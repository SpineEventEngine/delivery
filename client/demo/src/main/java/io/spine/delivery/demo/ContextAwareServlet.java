/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.demo;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.spine.client.Client;
import io.spine.core.TenantId;
import io.spine.logging.Logger;
import io.spine.logging.LoggingFactory;
import io.spine.logging.WithLogging;
import io.spine.delivery.DeliveryBootstrapper;
import io.spine.delivery.client.SimpleDeliveryClient;
import io.spine.server.BoundedContextBuilder;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Suppliers.ofInstance;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * An abstract base servlet for the demo Application.
 *
 * <p>Starts and configures the {@link GreeterContext demo} server and exposes a {@link Client} to
 * the server to inheritors.
 */
@SuppressWarnings("serial")
abstract class ContextAwareServlet extends HttpServlet implements WithLogging {

    private static final Logger logger = LoggingFactory.forEnclosingClass();

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
        channel = deliveryServerChannel();
        workRegistry = configureEnv();
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
        var server = "dns:///" + GCE_SERVER + ":8484";
        // Or use this one for local runs.
//        String server = "127.0.0.1:8484";
        return ManagedChannelBuilder
                .forTarget(server)
                .executor(limitedCachingExecutor)
                .usePlaintext()     // There is no SSL set up on GCE.
                .build();
    }

    private static Client inProcessClient() {
        var channel = InProcessChannelBuilder
                .forName(SERVER_NAME)
                .executor(limitedCachingExecutor)
                .build();
        return Client
                .usingChannel(channel)
                .build();
    }

    /**
     * Configures and starts the {@link GreeterContext demo} server.
     */
    private static Server startServer() {
        var demoContext = GreeterContext.builder();
        var server = Server.inProcess(SERVER_NAME)
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
    private static ShardedWorkRegistry configureEnv() {
        logger.atDebug()
              .log(() -> "Configuring `ServerEnvironment`.");
        var deliveryBuilder = DeliveryBootstrapper.newInstance()
                                                  .withChannel(ofInstance(channel))
                                                  .init();
        var delivery = deliveryBuilder
                .setStrategy(UniformAcrossAllShards.forNumber(NUMBER_OF_SHARDS))
                .build();
        delivery.subscribe(new AsyncLocalObserver(observerExecutor));
        ServerEnvironment
                .when(Production.class)
                .use(delivery)
                .use(InMemoryTransportFactory.newInstance())
                .use(InMemoryStorageFactory.newInstance());
        return deliveryBuilder.getWorkRegistry();
    }

    /**
     * An asynchronous shard observer that delivers the observed messages
     * on the {@linkplain #observerExecutor dedicated executor}.
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
            var delivery = ServerEnvironment.instance()
                                            .delivery();
            var index = update.shardIndex();
            executor.submit(() -> runDelivery(update, delivery, index));
        }

        @SuppressWarnings("HandleMethodResult")
        private static void runDelivery(InboxMessage message, Delivery delivery, ShardIndex index) {
            var tenant = message.tenant();
            TenantAwareRunner.with(tenant)
                             .run(() -> delivery.deliverMessagesFrom(index));
        }
    }
}
