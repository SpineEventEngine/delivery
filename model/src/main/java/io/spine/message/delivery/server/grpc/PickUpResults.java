/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.grpc;

import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.message.delivery.grpc.ShardPickUpResult;
import io.spine.message.delivery.rejection.Rejections;

public final class PickUpResults {

    /**
     * Prevent instantiation.
     */
    private PickUpResults() {
    }

    public static ShardPickUpResult pickedUp(ShardPickedUp pickedUp) {
        return ShardPickUpResult
                .newBuilder()
                .setPickedUp(pickedUp)
                .vBuild();
    }

    public static ShardPickUpResult alreadyPickedUp(Rejections.ShardAlreadyPickedUp alreadyPicked) {
        return ShardPickUpResult
                .newBuilder()
                .setAlreadyPicked(alreadyPicked)
                .vBuild();
    }
}
