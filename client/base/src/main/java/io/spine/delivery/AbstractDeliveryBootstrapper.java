/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.spine.delivery;

import io.grpc.ManagedChannel;
import io.spine.delivery.client.InboxClient;
import io.spine.delivery.client.RemoteInboxStorage;
import io.spine.delivery.client.RequestExecutionStrategy;
import io.spine.delivery.client.SessionRegistryClient;
import io.spine.delivery.client.WorkRegistry;
import io.spine.delivery.client.strategy.Propagate;
import io.spine.server.delivery.Delivery;
import io.spine.server.delivery.DeliveryBuilder;
import io.spine.server.delivery.InboxStorage;
import io.spine.server.delivery.ShardedWorkRegistry;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.util.Suppliers2.memoize;

/**
 * Provides a fluent API for building a {@link Delivery} backed by the Message Delivery Server.
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
     * Configures the delivery to be using the given {@code strategy} to handle possible failures
     * during interaction with the server.
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
     * Returns the typed bootstrapper.
     */
    protected abstract T self();

    /**
     * Returns the configured {@code RequestExecutionStrategy} or {@link Propagate} strategy
     * if it was not customized.
     */
    protected final RequestExecutionStrategy executionStrategy() {
        return strategy;
    }

    /**
     * Creates a new {@code InboxClient} that uses the supplied {@code channel}.
     */
    protected abstract InboxClient newInboxClient(Supplier<ManagedChannel> channel);

    /**
     * Creates a new {@code SessionRegistryClient} that uses the supplied {@code channel}.
     */
    protected abstract SessionRegistryClient
    newSessionRegistryClient(Supplier<ManagedChannel> channel);
}
