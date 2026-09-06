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

package io.spine.delivery.client;

import io.spine.delivery.event.ShardPickedUp;
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
        return ShardSessionRecord.newBuilder()
                .setIndex(pickedUp.getShard())
                .setWorker(pickedUp.getWorker())
                .setWhenLastPicked(pickedUp.getWhenPicked())
                .build();
    }
}
