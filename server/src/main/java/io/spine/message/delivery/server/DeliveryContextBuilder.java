/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import com.google.common.annotations.VisibleForTesting;
import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;

/**
 * The builder of the Delivery {@link BoundedContext}.
 */
public final class DeliveryContextBuilder {

    /**
     * Creates a new builder instance.
     *
     * <p>To initialize this builder outside of the package use
     * {@link DeliveryContext#newBuilder()}.
     */
    DeliveryContextBuilder() {
    }

    /**
     * Initializes the {@code BoundedContext}.
     */
    public BoundedContext build(){
        var contextBuilder = contextBuilder();
        return contextBuilder.build();
    }

    /**
     * Returns a fully-initialized instance of the Delivery {@code BoundedContextBuilder}.
     */
    @VisibleForTesting
    public BoundedContextBuilder contextBuilder() {
        return BoundedContext
                .singleTenant(DeliveryContext.NAME)
                .add(InboxStorageState.class)
                .addCommandDispatcher(new InboxWriter());
    }
}
