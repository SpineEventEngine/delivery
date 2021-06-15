/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import com.google.common.annotations.VisibleForTesting;
import io.spine.base.Time;
import io.spine.core.CommandContext;
import io.spine.logging.Logging;
import io.spine.message.delivery.server.command.PickUpShard;
import io.spine.message.delivery.server.command.ReleaseShard;
import io.spine.message.delivery.server.event.ShardPickedUp;
import io.spine.message.delivery.server.event.ShardReleased;
import io.spine.message.delivery.server.rejection.ShardAlreadyPickedUp;
import io.spine.message.delivery.server.rejection.UnableToReleaseShard;
import io.spine.protobuf.Messages;
import io.spine.server.NodeId;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.Apply;
import io.spine.server.command.Assign;
import io.spine.server.delivery.ShardIndex;

import static io.spine.message.delivery.server.rejection.Rejections.UnableToReleaseShard.Reason;
import static java.lang.String.format;

final class SessionRegistry
        extends Aggregate<ShardIndex, ShardSessionRegistry, ShardSessionRegistry.Builder>
        implements Logging {

    @Assign
    ShardPickedUp handle(PickUpShard c, CommandContext context) throws ShardAlreadyPickedUp {
        var shard = c.getShard();
        var worker = c.getWorker();
        _debug().log("Worker `%s` is picking up shard `%s`.", worker, shard);
        checkNotPickedUp();
        return ShardPickedUp.newBuilder()
                .setShard(shard)
                .setPickedBy(worker)
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

    @Assign
    ShardReleased handle(ReleaseShard c, CommandContext context) throws UnableToReleaseShard {
        var shard = c.getShard();
        var worker = c.getWorker();
        _debug().log("Worker `%s` is releasing shard `%s`.", worker, shard);
        checkCanRelease(c);
        return ShardReleased.newBuilder()
                .setShard(shard)
                .setPickedBy(worker)
                .setWhenReleased(Time.currentTime())
                .vBuild();
    }

    private void checkCanRelease(ReleaseShard c) throws UnableToReleaseShard {
        var state = state();
        var currentWorker = state.getPickedBy();
        if (Messages.isDefault(currentWorker)) {
            throw unableToRelease(c, shardNotPickedUp());
        }
        if (!currentWorker.equals(c.getWorker())) {
            throw unableToRelease(c, shardPickedUpByOtherWorker(currentWorker));
        }
    }

    private UnableToReleaseShard unableToRelease(ReleaseShard cause, Reason reason) {
        return UnableToReleaseShard.newBuilder()
                .setShard(id())
                .setWorker(cause.getWorker())
                .setReason(reason)
                .build();
    }

    @VisibleForTesting
    static Reason shardNotPickedUp() {
        return newReason("Shard is not picked up by any worker.");
    }

    @VisibleForTesting
    static Reason shardPickedUpByOtherWorker(NodeId currentWorker) {
        return newReason(format(
                "Shard is picked up by worker `%s` and cannot be released by another worker.",
                currentWorker
        ));
    }

    private static Reason newReason(String reason) {
        return Reason.newBuilder()
                .setValue(reason)
                .vBuild();
    }

    @Apply
    @SuppressWarnings({
            "ResultOfMethodCallIgnored" /* that's OK. We're clearing state fields */,
            "PMD.UnusedFormalParameter"
    })
    private void on(ShardReleased e) {
        builder().clearPickedBy()
                 .clearWhenPicked();
    }
}
