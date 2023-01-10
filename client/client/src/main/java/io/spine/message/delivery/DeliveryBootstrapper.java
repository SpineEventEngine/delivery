/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery;

import io.grpc.ManagedChannel;
import io.spine.message.delivery.client.DeliveryClient;
import io.spine.message.delivery.client.RequestExecutionStrategy;
import io.spine.message.delivery.client.InboxClient;
import io.spine.message.delivery.client.Propagate;
import io.spine.message.delivery.client.SessionRegistryClient;
import io.spine.server.delivery.Delivery;

import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Provides fluent API for building a {@link Delivery} backed by the Message Delivery Server and
 * based on the {@link DeliveryClient} with {@link RequestExecutionStrategy}.
 *
 * <p> By default uses the {@link Propagate} strategy.
 */
public final class DeliveryBootstrapper extends AbstractDeliveryBootstrapper<DeliveryBootstrapper> {

    private RequestExecutionStrategy strategy = new Propagate();

    /**
     * Prevents direct instantiation.
     */
    private DeliveryBootstrapper() {
        super();
    }

    /**
     * Creates a new instance of this bootstrapper.
     */
    public static DeliveryBootstrapper newInstance() {
        return new DeliveryBootstrapper();
    }

    /**
     * Configures delivery to be using the given {@code strategy} to handle possible failures
     * during interaction with server.
     */
    public DeliveryBootstrapper withErrorStrategy(RequestExecutionStrategy strategy) {
        this.strategy = checkNotNull(strategy);
        return self();
    }

    @Override
    protected DeliveryBootstrapper self() {
        return this;
    }

    @Override
    protected InboxClient newInboxClient(Supplier<ManagedChannel> channel) {
        return DeliveryClient.create(channel.get(), strategy);
    }

    @Override
    protected SessionRegistryClient newSessionRegistryClient(Supplier<ManagedChannel> channel) {
        return DeliveryClient.create(channel.get(), strategy);
    }
}
