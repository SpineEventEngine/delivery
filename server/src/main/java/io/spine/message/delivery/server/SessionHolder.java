/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import io.spine.core.Subscribe;
import io.spine.message.delivery.ShardSessionHolder;
import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.message.delivery.event.ShardReleased;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.projection.Projection;

public class SessionHolder
        extends Projection<ShardIndex, ShardSessionHolder, ShardSessionHolder.Builder> {

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
