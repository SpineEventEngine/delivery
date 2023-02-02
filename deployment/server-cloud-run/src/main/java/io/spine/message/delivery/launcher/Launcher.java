/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.launcher;

import io.spine.logging.Logging;
import io.spine.message.delivery.admin.AdminServer;
import io.spine.message.delivery.server.App;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static java.lang.Boolean.parseBoolean;

/**
 * An entrypoint for launching Liquor server.
 */
public final class Launcher implements Logging {

    private static final String ADMIN_SERVER_ENV = "ADMIN_SERVER";

    private Launcher() {
    }

    public static void main(String[] args) throws InterruptedException {
        new Launcher().launch(args);
    }

    /**
     * Launches the Liquor server, also launches the Admin Server if configured.
     *
     * <p>Blocks execution until the Liquor server stops.
     *
     * @param args
     *         command line arguments that were passed with the startup command. This parameter will
     *         be passed to both Liquor and Admin server as is.
     */
    private void launch(String[] args) throws InterruptedException {
        ThreadFactory threadFactory = Executors.defaultThreadFactory();
        Thread liquor = liquor(threadFactory, args);
        liquor.start();
        if (useAdminServer()) {
            _info().log("Starting Admin Server.");
            admin(threadFactory, args).start();
        } else {
            _info().log("Admin Server start skipped.");
        }
        liquor.join();
    }

    /**
     * Creates a new {@code Thread} that starts and executes the Liquor server code.
     *
     * @param threads
     *         factory to create a new thread
     * @param args
     *         startup arguments
     */
    private static Thread liquor(ThreadFactory threads, String[] args) {
        Thread liquor = threads.newThread(() -> App.main(args));
        liquor.setName("liquor");
        return liquor;
    }

    /**
     * Creates new daemon {@code Thread} that starts and executes the Admin Server code.
     *
     * @param threads
     *         factory to create a new thread
     * @param args
     *         startup arguments
     */
    private static Thread admin(ThreadFactory threads, String[] args) {
        Thread adminServer = threads.newThread(() -> AdminServer.main(args));
        adminServer.setDaemon(true);
        adminServer.setName("admin");
        return adminServer;
    }

    /**
     * Reads the {@code ADMIN_SERVER} environment variable and returns {@code true} only if its
     * value equals ignore case to "true" and returns {@code false} otherwise.
     */
    @SuppressWarnings("CallToSystemGetenv")
    private static boolean useAdminServer() {
        return parseBoolean(System.getenv(ADMIN_SERVER_ENV));
    }
}
