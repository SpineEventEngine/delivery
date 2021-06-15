/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import io.spine.base.Time;
import io.spine.core.CommandContext;
import io.spine.message.delivery.server.command.PickUpShard;
import io.spine.message.delivery.server.event.ShardPickedUp;
import io.spine.message.delivery.server.rejection.ShardAlreadyPickedUp;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.Apply;
import io.spine.server.command.Assign;
import io.spine.server.delivery.ShardIndex;

final class SessionRegistry
        extends Aggregate<ShardIndex, ShardSessionRegistry, ShardSessionRegistry.Builder> {

    @Assign
    ShardPickedUp handle(PickUpShard c, CommandContext context) throws ShardAlreadyPickedUp {
        checkNotPickedUp();
        return ShardPickedUp.newBuilder()
                .setShard(c.getShard())
                .setPickedBy(c.getWorker())
                .setWhenPicked(Time.currentTime())
                .vBuild();
    }

    private void checkNotPickedUp() throws ShardAlreadyPickedUp {
        var state = state();
        if (state.hasPickedBy()) {
            throw ShardAlreadyPickedUp.newBuilder()
                    .setShard(id())
                    .setWorker(state.getPickedBy())
                    .build();
        }
    }

    @Apply
    private void on(ShardPickedUp e) {
        builder().setPickedBy(e.getPickedBy())
                 .setWhenPicked(e.getWhenPicked());
    }
}
