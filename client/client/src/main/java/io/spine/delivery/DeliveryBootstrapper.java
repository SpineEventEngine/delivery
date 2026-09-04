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
import io.spine.delivery.client.DeliveryClient;
import io.spine.delivery.client.InboxClient;
import io.spine.delivery.client.RequestExecutionStrategy;
import io.spine.delivery.client.SessionRegistryClient;
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
