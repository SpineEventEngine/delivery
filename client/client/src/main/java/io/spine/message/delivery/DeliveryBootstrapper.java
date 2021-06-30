/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery;

import com.google.common.base.Suppliers;
import io.grpc.ManagedChannel;
import io.spine.message.delivery.client.DeliveryClient;
import io.spine.message.delivery.client.RemoteInboxStorage;
import io.spine.message.delivery.client.WorkRegistry;
import io.spine.server.delivery.Delivery;
import io.spine.server.delivery.DeliveryBuilder;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Provides fluent API for building a {@link Delivery} backed by the Message Delivery Server and
 * based on the {@link DeliveryClient}.
 */
public final class DeliveryBootstrapper {

    private ManagedChannel channel;

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

    /**
     * Configures the gRPC {@code channel} to be used by the delivery.
     */
    public DeliveryBootstrapper withChannel(ManagedChannel channel) {
        this.channel = checkNotNull(channel);
        return this;
    }

    /**
     * Initializes the underlying {@code DeliveryBuilder} using this bootstrapper configurations.
     */
    public DeliveryBuilder init() {
        checkNotNull(channel, "The gRPC channel must not be `null`.");
        DeliveryClient client = DeliveryClient.create(channel);
        return Delivery.newBuilder()
                .setInboxStorage(new RemoteInboxStorage(Suppliers.ofInstance(client)))
                .setWorkRegistry(new WorkRegistry(Suppliers.ofInstance(client)));
    }
}
