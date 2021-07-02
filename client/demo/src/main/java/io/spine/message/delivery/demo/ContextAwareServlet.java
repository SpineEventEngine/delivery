/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.demo;

import com.google.appengine.api.ThreadManager;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.spine.base.Production;
import io.spine.client.Client;
import io.spine.logging.Logging;
import io.spine.message.delivery.DeliveryBootstrapper;
import io.spine.message.delivery.client.DeliveryClient;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.DeploymentType;
import io.spine.server.Server;
import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.Delivery;
import io.spine.server.delivery.LocalDispatchingObserver;
import io.spine.server.delivery.UniformAcrossAllShards;
import io.spine.server.storage.memory.InMemoryStorageFactory;
import io.spine.server.transport.memory.InMemoryTransportFactory;

import javax.servlet.http.HttpServlet;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Suppliers.memoize;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * An abstract base servlet for the demo Application.
 *
 * <p>Starts and configures {@link DemoContext demo} server and exposes a {@link Client} to the
 * server to inheritors.
 */
@SuppressWarnings("serial")
abstract class ContextAwareServlet extends HttpServlet implements Logging {

    /** The number of shards used for the signal delivery. **/
    private static final int NUMBER_OF_SHARDS = 50;
    protected static final Supplier<DeliveryClient> client = memoize(ContextAwareServlet::cloudRunClient);
    protected static final String SERVER_NAME = "DemoServer";
    protected static final Server server;
    protected static final Client spineClient;

    static {
        useLog4j2FloggerBackend();
        configureEnv();
        server = startServer();
        spineClient = Client
                .inProcess(SERVER_NAME)
                .build();
    }

    private static DeliveryClient cloudRunClient() {
        return DeliveryClient.create(deliveryServerChannel());
    }

    @SuppressWarnings(
            "CallToSystemGetenv" /* We do want to use env variable for the server location. */
    )
    private static ManagedChannel deliveryServerChannel() {
        String server = System.getenv("DELIVERY_SERVER");
        if (isNullOrEmpty(server)) {
            server = "dns:///message-delivery-server-irtlrrb2aq-uc.a.run.app:443";
        }
        ServerEnvironment env = ServerEnvironment.instance();
        DeploymentType deployment = env.deploymentType();
        ThreadFactory threads;
        if (deployment == DeploymentType.APPENGINE_CLOUD) {
            threads = ThreadManager.currentRequestThreadFactory();
        } else {
            threads = Executors.defaultThreadFactory();
        }
        ExecutorService executor = Executors.newCachedThreadPool(threads);
        return ManagedChannelBuilder
                .forTarget(server)
                .executor(executor)
                .build();
    }

    /**
     * Configures and starts the {@link DemoContext demo} server.
     */
    private static Server startServer() {
        BoundedContextBuilder demoContext = DemoContext.builder();
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
        ServerEnvironment
                .when(Production.class)
                .useDelivery((env) -> {
                    Delivery delivery = DeliveryBootstrapper.newInstance()
                            .withChannel(deliveryServerChannel())
                            .init()
                            .setStrategy(UniformAcrossAllShards.forNumber(NUMBER_OF_SHARDS))
                            .build();
                    delivery.subscribe(new LocalDispatchingObserver());
                    return delivery;
                })
                .use(InMemoryTransportFactory.newInstance())
                .use(InMemoryStorageFactory.newInstance());
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
