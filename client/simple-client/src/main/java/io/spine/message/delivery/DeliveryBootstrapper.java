/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery;

import io.grpc.ManagedChannel;
import io.spine.message.delivery.client.InboxClient;
import io.spine.message.delivery.client.SessionRegistryClient;
import io.spine.message.delivery.client.SimpleDeliveryClient;
import io.spine.server.delivery.Delivery;

import java.util.function.Supplier;

/**
 * Provides fluent API for building a {@link Delivery} backed by the Message Delivery Server and
 * based on the {@link SimpleDeliveryClient}.
 */
public final class DeliveryBootstrapper extends AbstractDeliveryBootstrapper<DeliveryBootstrapper> {

    /**
     * Prevents direct instantiation.
     */
    private DeliveryBootstrapper() {
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
        return SimpleDeliveryClient.create(channel.get());
    }

    @Override
    protected SessionRegistryClient newSessionRegistryClient(Supplier<ManagedChannel> channel) {
        return SimpleDeliveryClient.create(channel.get());
    }
}
