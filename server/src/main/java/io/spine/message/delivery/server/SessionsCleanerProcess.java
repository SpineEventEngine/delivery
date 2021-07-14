/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import io.spine.base.Time;
import io.spine.client.Client;
import io.spine.core.CommandContext;
import io.spine.message.delivery.command.ReleaseExpiredSessions;
import io.spine.message.delivery.command.ReleaseShard;
import io.spine.message.delivery.event.ExpiredShardsReleased;
import io.spine.message.delivery.event.ShardReleased;
import io.spine.message.delivery.rejection.UnableToReleaseShard;
import io.spine.server.command.AbstractCommander;
import io.spine.server.command.Assign;
import io.spine.server.command.Command;

public class SessionsCleanerProcess extends AbstractCommander {

    private Client client;


    @Assign
    ExpiredShardsReleased handle(ReleaseExpiredSessions c, CommandContext context) {
        var shard = c.getShard();
        var worker = c.getWorker();
        _debug().log("Worker `%s` is releasing shard `%s`.", worker, shard);
        checkCanRelease(c);
        _info().log("Shard `%s` is released by worker `%s`.", shard, worker);
        return ShardReleased.newBuilder()
                .setShard(shard)
                .setPickedBy(worker)
                .setWhenReleased(Time.currentTime())
                .vBuild();
    }
}
