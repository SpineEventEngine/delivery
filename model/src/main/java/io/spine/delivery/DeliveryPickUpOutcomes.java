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
