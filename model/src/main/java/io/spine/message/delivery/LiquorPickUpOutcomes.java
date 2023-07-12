/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery;

import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.message.delivery.rejection.Rejections;

import static io.spine.util.Preconditions2.checkNotDefaultArg;

/**
 * A utility to construct {@code LiquorPickUpOutcome} with possible inputs.
 */
public final class LiquorPickUpOutcomes {

    private LiquorPickUpOutcomes() {
    }

    /**
     * Creates a new {@code LiquorPickUpOutcome} with the given {@code event} identifying
     * successfully pick up operation.
     */
    public static LiquorPickUpOutcome pickedUp(ShardPickedUp event) {
        checkNotDefaultArg(event);
        return LiquorPickUpOutcome
                .newBuilder()
                .setPickedUp(event)
                .vBuild();
    }

    /**
     * Creates a new {@code LiquorPickUpOutcome} with the given {@code rejection} identifying that
     * shard is already picked up by another worker.
     */
    public static LiquorPickUpOutcome alreadyPickedUp(Rejections.ShardAlreadyPickedUp rejection) {
        checkNotDefaultArg(rejection);
        return LiquorPickUpOutcome
                .newBuilder()
                .setAlreadyPickedUp(rejection)
                .vBuild();
    }
}
