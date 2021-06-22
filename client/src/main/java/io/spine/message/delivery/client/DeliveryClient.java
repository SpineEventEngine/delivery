/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.spine.client.Client;

import static io.spine.util.Preconditions2.checkNotEmptyOrBlank;
import static io.spine.util.Preconditions2.checkPositive;

final class DeliveryClient {

    private final Client client;

    private DeliveryClient(ManagedChannel channel) {
        client = Client
                .usingChannel(channel)
                .withGuestId("DeliveryClient")
                .build();
    }

    /**
     * Creates a new delivery client which connects to a local gRPC server on port {@code 8484}.
     */
    static DeliveryClient local() {
        return create("127.0.0.1", 8484);
    }

    /**
     * Creates a new delivery client which connects to a gRPC server on the specified {@code host}
     * and {@code port}.
     */
    static DeliveryClient create(String host, int port) {
        checkNotEmptyOrBlank(host);
        checkPositive(port);
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();
        return new DeliveryClient(channel);
    }
}
