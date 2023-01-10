/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery;

import io.grpc.ManagedChannel;
import io.spine.message.delivery.client.InboxClient;
import io.spine.message.delivery.client.Propagate;
import io.spine.message.delivery.client.RemoteInboxStorage;
import io.spine.message.delivery.client.RequestExecutionStrategy;
import io.spine.message.delivery.client.SessionRegistryClient;
import io.spine.message.delivery.client.WorkRegistry;
import io.spine.server.delivery.Delivery;
import io.spine.server.delivery.DeliveryBuilder;
import io.spine.server.delivery.InboxStorage;
import io.spine.server.delivery.ShardedWorkRegistry;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Suppliers.memoize;

/**
 * Provides fluent API for building a {@link Delivery} backed by the Message Delivery Server.
 *
 * @param <T>
 *         the type of the bootstrapper
 */
public abstract class AbstractDeliveryBootstrapper<T extends AbstractDeliveryBootstrapper<T>> {

    private @MonotonicNonNull Supplier<ManagedChannel> channel;

    private RequestExecutionStrategy strategy = new Propagate();

    /**
     * Configures the gRPC {@code channel} to be used by the delivery.
     */
    public final T withChannel(Supplier<ManagedChannel> channel) {
        this.channel = checkNotNull(channel);
        return self();
    }

    /**
     * Configures delivery to be using the given {@code strategy} to handle possible failures
     * during interaction with server.
     */
    public final T withExecutionStrategy(RequestExecutionStrategy strategy) {
        this.strategy = checkNotNull(strategy);
        return self();
    }

    /**
     * Initializes the underlying {@code DeliveryBuilder} using this bootstrapper configurations.
     */
    public final DeliveryBuilder init() {
        checkNotNull(channel, "The gRPC channel must not be `null`.");
        InboxStorage inboxStorage = new RemoteInboxStorage(
                memoize(() -> newInboxClient(channel))
        );
        ShardedWorkRegistry workRegistry = new WorkRegistry(
                memoize(() -> newSessionRegistryClient(channel))
        );
        return Delivery.newBuilder()
                .setInboxStorage(inboxStorage)
                .setWorkRegistry(workRegistry);
    }

    /**
     * Returns typed bootstrapper.
     */
    protected abstract T self();

    /**
     * Returns configured {@code RequestExecutionStrategy} or {@link Propagate} strategy if
     * was not configured.
     */
    protected final RequestExecutionStrategy executionStrategy() {
        return strategy;
    }

    /**
     * Creates a new {@code InboxClient} which uses supplied {@code channel}.
     */
    protected abstract InboxClient newInboxClient(Supplier<ManagedChannel> channel);

    /**
     * Creates a new {@code SessionRegistryClient} which uses supplied {@code channel}.
     */
    protected abstract SessionRegistryClient
    newSessionRegistryClient(Supplier<ManagedChannel> channel);
}
