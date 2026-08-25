/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery;

import io.grpc.ManagedChannel;
import io.spine.delivery.client.InboxClient;
import io.spine.delivery.client.RequestExecutionStrategy;
import io.spine.delivery.client.SessionRegistryClient;
import io.spine.delivery.client.DeliveryClient;
import io.spine.delivery.client.strategy.Propagate;
import io.spine.server.delivery.Delivery;

import java.util.function.Supplier;

/**
 * Provides a fluent API for building a {@link Delivery} backed by the Message Delivery Server and
 * based on the {@link DeliveryClient} with {@link RequestExecutionStrategy}.
 *
 * <p>By default uses the {@link Propagate} strategy.
 */
public final class DeliveryBootstrapper extends AbstractDeliveryBootstrapper<DeliveryBootstrapper> {

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

    @Override
    protected DeliveryBootstrapper self() {
        return this;
    }

    @Override
    protected InboxClient newInboxClient(Supplier<ManagedChannel> channel) {
        return DeliveryClient.create(channel.get(), executionStrategy());
    }

    @Override
    protected SessionRegistryClient newSessionRegistryClient(Supplier<ManagedChannel> channel) {
        return DeliveryClient.create(channel.get(), executionStrategy());
    }
}
