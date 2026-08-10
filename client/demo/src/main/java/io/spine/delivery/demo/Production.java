/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.demo;

import io.spine.environment.CustomEnvironmentType;
import io.spine.environment.Tests;

/**
 * Declares a “production” environment.
 *
 * <p>When this environment is used the system is meant to run and work as it should run
 * in production.
 */
public final class Production extends CustomEnvironmentType<Production> {

    private static final Production INSTANCE = new Production();

    private Production() {
        super();
    }

    /**
     * Obtains the singleton instance.
     */
    static Production type() {
        return INSTANCE;
    }

    @Override
    protected boolean enabled() {
        var isTests = Tests.type()
                           .enabled();
        return !isTests;
    }

    @Override
    protected Production self() {
        return this;
    }
}
