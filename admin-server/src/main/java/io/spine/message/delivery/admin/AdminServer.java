/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.admin;

import io.micronaut.runtime.Micronaut;

/**
 * Spine Liquor admin server application.
 */
public final class AdminServer {

    private AdminServer() {
    }

    public static void main(String[] args) {
        Micronaut.run(args);
    }
}
