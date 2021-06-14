/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

/**
 * Defines a bounded context for the Delivery application.
 */
public final class DeliveryContext {

    /**
     * The {@linkplain io.spine.core.BoundedContextName name} of the initialized bounded context.
     */
    static final String NAME = "Delivery";

    /**
     * Prevents direct instantiation.
     */
    private DeliveryContext() {
    }

    /**
     * Creates a new builder of this context.
     */
    public static DeliveryContextBuilder newBuilder() {
        return new DeliveryContextBuilder();
    }
}
