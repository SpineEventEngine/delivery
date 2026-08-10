/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.admin;

import io.micronaut.runtime.Micronaut;

/**
 * Spine Delivery admin server application.
 */
public final class AdminServer {

    private AdminServer() {
    }

    /**
     * Runs the Micronaut-based {@code AdminServer}.
     *
     * @param args
     *         command line arguments that will be passed to the Micronaut. In most cases
     *         there is no need to pass any arguments to make the server run properly;
     *         these arguments are provided for cases when some modification required.
     */
    public static void main(String[] args) {
        Micronaut.run(args);
    }
}
