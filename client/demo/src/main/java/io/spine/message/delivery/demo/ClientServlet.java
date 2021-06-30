/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.demo;

import com.google.appengine.api.ThreadManager;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.spine.message.delivery.client.DeliveryClient;

import javax.servlet.http.HttpServlet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Suppliers.memoize;

@SuppressWarnings("serial")
public class ClientServlet extends HttpServlet {

    static {
        useLog4j2FloggerBackend();
    }

    protected static final Supplier<DeliveryClient> client = memoize(ClientServlet::cloudRunClient);

    @SuppressWarnings(
            "CallToSystemGetenv" /* We do want to use env variable for the server location. */
    )
    private static DeliveryClient cloudRunClient() {
        String server = System.getenv("DELIVERY_SERVER");
        if (isNullOrEmpty(server)) {
            server = "dns:///message-delivery-server-jxnqoshxfq-uc.a.run.app:443";
        }
        ThreadFactory threads = ThreadManager.currentRequestThreadFactory();
        ExecutorService executor = Executors.newCachedThreadPool(threads);
        ManagedChannel channel = ManagedChannelBuilder
                .forTarget(server)
                .executor(executor)
                .build();
        return DeliveryClient.create(channel);
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
