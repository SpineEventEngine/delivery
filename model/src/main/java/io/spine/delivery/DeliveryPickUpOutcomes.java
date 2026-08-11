/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery;

import io.spine.delivery.event.ShardPickedUp;
import io.spine.delivery.rejection.Rejections;

import static io.spine.util.Preconditions2.checkNotDefaultArg;

/**
 * A utility to construct {@code DeliveryPickUpOutcome} with possible inputs.
 */
public final class DeliveryPickUpOutcomes {

    private DeliveryPickUpOutcomes() {
    }

    /**
     * Creates a new {@code DeliveryPickUpOutcome} with the given {@code event} identifying
     * successfully pick up operation.
     */
    public static DeliveryPickUpOutcome pickedUp(ShardPickedUp event) {
        checkNotDefaultArg(event);
        return DeliveryPickUpOutcome.newBuilder()
                .setPickedUp(event)
                .build();
    }

    /**
     * Creates a new {@code DeliveryPickUpOutcome} with the given {@code rejection} identifying that
     * the shard is already picked up by another worker.
     */
    public static DeliveryPickUpOutcome alreadyPickedUp(Rejections.ShardAlreadyPickedUp rejection) {
        checkNotDefaultArg(rejection);
        return DeliveryPickUpOutcome.newBuilder()
                .setAlreadyPickedUp(rejection)
                .build();
    }
}
