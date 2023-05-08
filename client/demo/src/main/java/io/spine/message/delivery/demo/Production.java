/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.demo;

import io.spine.environment.CustomEnvironmentType;
import io.spine.environment.Tests;

/**
 * Non-testing environment.
 */
public class Production extends CustomEnvironmentType<Production> {

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
        boolean isTests = Tests.type()
                               .enabled();
        return !isTests;
    }

    @Override
    protected Production self() {
        return this;
    }
}
