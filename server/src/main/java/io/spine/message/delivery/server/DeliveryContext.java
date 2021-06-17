/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import com.google.common.annotations.VisibleForTesting;
import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.CommandService;
import io.spine.server.QueryService;
import io.spine.server.SubscriptionService;

/**
 * Defines a bounded context for the Delivery application.
 */
public final class DeliveryContext {

    /**
     * The {@linkplain io.spine.core.BoundedContextName name} of the initialized bounded context.
     */
    static final String NAME = "Delivery";

    private final BoundedContext context;

    /**
     * Prevents direct instantiation.
     */
    private DeliveryContext(BoundedContext context) {
        this.context = context;
    }

    /**
     * Creates a new instance of a {@code CommandService} with this context.
     */
    public CommandService commandService() {
        return CommandService.newBuilder()
                .add(context)
                .build();
    }

    /**
     * Creates a new instance of a {@code QueryService} with this context.
     */
    public QueryService queryService() {
        return QueryService.newBuilder()
                .add(context)
                .build();
    }

    /**
     * Creates a new instance of a {@code SubscriptionService} with this context.
     */
    public SubscriptionService subscriptionService(){
        return SubscriptionService.newBuilder()
                .add(context)
                .build();
    }

    /**
     * Creates a new builder of this context.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * The builder of the Delivery {@link BoundedContext}.
     */
    public static final class Builder {

        /**
         * Creates a new builder instance.
         */
        private Builder() {
        }

        /**
         * Initializes the {@code BoundedContext}.
         */
        public DeliveryContext build() {
            var contextBuilder = context();
            return new DeliveryContext(contextBuilder.build());
        }

        /**
         * Returns a fully-initialized instance of the Delivery {@code BoundedContextBuilder}.
         */
        @VisibleForTesting
        public BoundedContextBuilder context() {
            return BoundedContext
                    .singleTenant(NAME)
                    .add(new ShardedInboxStorageRepo())
                    .add(SessionRegistry.class)
                    .addCommandDispatcher(new InboxWriter());
        }
    }
}
