/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.server;

import io.spine.core.Subscribe;
import io.spine.delivery.CurrentShardState;
import io.spine.delivery.event.ShardPickedUp;
import io.spine.delivery.event.ShardReleased;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.projection.Projection;

/**
 * Represents the current state of a particular shard in the system.
 */
final class CurrentShardStateProjection
        extends Projection<ShardIndex, CurrentShardState, CurrentShardState.Builder> {

    @Subscribe
    void on(ShardPickedUp e) {
        builder().setWorker(e.getWorker())
                 .setWhenLastPicked(e.getWhenPicked());
    }

    @Subscribe
    void on(ShardReleased e) {
        builder().clearWorker();
    }
}
