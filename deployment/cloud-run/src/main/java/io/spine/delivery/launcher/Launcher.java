/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.launcher;

import com.google.common.annotations.VisibleForTesting;
import io.spine.delivery.admin.AdminServer;
import io.spine.delivery.server.DeliveryServerApp;
import io.spine.logging.WithLogging;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static java.lang.Boolean.parseBoolean;

/**
 * An entrypoint for launching the Delivery server.
 */
public final class Launcher implements WithLogging {

    private static final String ADMIN_SERVER_ENV = "ADMIN_SERVER";

    private Launcher() {
    }

    /**
     * Starts the {@code Launcher}.
     *
     * @param args
     *         arguments passed to the program through the command line. Those arguments
     *         will be passed to Delivery and Admin services as is. There is no need to pass any
     *         arguments unless one wants to modify the server startup.
     */
    public static void main(String[] args) throws InterruptedException {
        new Launcher().launch(args);
    }

    /**
     * Launches the Delivery server; also launches the Admin Server if configured.
     *
     * <p>Blocks execution until the Delivery server stops.
     *
     * @param args
     *         command line arguments that were passed with the startup command. This parameter will
     *         be passed to both Delivery and Admin server as is.
     */
    private void launch(String[] args) throws InterruptedException {
        var threadFactory = Executors.defaultThreadFactory();
        var delivery = delivery(threadFactory, args);
        delivery.start();
        if (useAdminServer()) {
            logger().atInfo().log(() -> "Starting Admin Server.");
            admin(threadFactory, args).start();
        } else {
            logger().atInfo().log(() -> "Admin Server start skipped.");
        }
        delivery.join();
    }

    /**
     * Creates a new {@code Thread} that starts and executes the Delivery server code.
     *
     * @param threads
     *         factory to create a new thread
     * @param args
     *         startup arguments
     */
    @VisibleForTesting
    static Thread delivery(ThreadFactory threads, String[] args) {
        var delivery = threads.newThread(() -> DeliveryServerApp.main(args));
        delivery.setName("delivery");
        return delivery;
    }

    /**
     * Creates a new daemon {@code Thread} that starts and executes the Admin Server code.
     *
     * <p>The thread is a daemon one so that it never keeps the container alive after
     * the Delivery server, which the launcher waits for, has stopped.
     *
     * @param threads
     *         factory to create a new thread
     * @param args
     *         startup arguments
     */
    @VisibleForTesting
    static Thread admin(ThreadFactory threads, String[] args) {
        var adminServer = threads.newThread(() -> AdminServer.main(args));
        adminServer.setDaemon(true);
        adminServer.setName("admin");
        return adminServer;
    }

    /**
     * Reads the {@code ADMIN_SERVER} environment variable and returns {@code true} only if its
     * value equals ignore case to "true" and returns {@code false} otherwise.
     */
    @SuppressWarnings("CallToSystemGetenv")
    @VisibleForTesting
    static boolean useAdminServer() {
        return parseBoolean(System.getenv(ADMIN_SERVER_ENV));
    }
}
