/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.server.delivery.ShardSessionRecord;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A utility for working with {@linkplain ShardSessionRecord}.
 */
public final class ShardSessionRecords {

    private ShardSessionRecords() {
    }

    /**
     * Creates a new {@code ShardSessionRecord} out of the given {@code ShardPickedUp} event.
     */
    public static ShardSessionRecord fromEvent(ShardPickedUp pickedUp) {
        checkNotNull(pickedUp);
        return ShardSessionRecord
                .newBuilder()
                .setIndex(pickedUp.getShard())
                .setWorker(pickedUp.getWorker())
                .setWhenLastPicked(pickedUp.getWhenPicked())
                .vBuild();
    }
}
