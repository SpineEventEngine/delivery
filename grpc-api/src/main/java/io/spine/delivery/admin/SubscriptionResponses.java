/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.admin;

import io.spine.delivery.admin.grpc.ShardInfoUpdate;
import io.spine.delivery.admin.grpc.SubscriptionResponse;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Utility that helps to create {@code SubscriptionResponse}s.
 */
public final class SubscriptionResponses {

    private SubscriptionResponses() {
    }

    /**
     * Creates a new {@code SubscriptionResponse} with the acknowledgement of the subscription.
     */
    public static SubscriptionResponse ack() {
        return SubscriptionResponse
                .newBuilder()
                .setCreated(true)
                .build();
    }

    /**
     * Creates a new {@code SubscriptionResponse} with the given {@code update}.
     */
    public static SubscriptionResponse toResponse(ShardInfoUpdate update) {
        checkNotNull(update);
        return SubscriptionResponse
                .newBuilder()
                .setUpdate(update)
                .build();
    }
}
