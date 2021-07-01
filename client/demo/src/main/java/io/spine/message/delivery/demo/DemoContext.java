/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.demo;

import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;

import java.security.SecureRandom;
import java.util.Random;

/**
 * Represents the context of this demo application.
 */
final class DemoContext {

    private static final Random random = new SecureRandom();
    static final String contextName = "Demo";

    /**
     * Prevents instantiation.
     */
    private DemoContext() {
    }

    /**
     * Returns a new instance of this context.
     */
    static BoundedContext newInstance() {
        return builder().build();
    }

    /**
     * Returns a fully-initialized context builder.
     */
    static BoundedContextBuilder builder() {
        return BoundedContext
                .singleTenant(contextName)
                .add(new GreeterRepo(random));
    }
}
