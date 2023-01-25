/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import com.google.common.annotations.VisibleForTesting;
import io.spine.base.Time;
import io.spine.core.CommandContext;
import io.spine.logging.Logging;
import io.spine.message.delivery.ShardSessionRegistry;
import io.spine.message.delivery.command.PickUpShard;
import io.spine.message.delivery.command.ReleaseShard;
import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.message.delivery.event.ShardReleased;
import io.spine.message.delivery.rejection.ShardAlreadyPickedUp;
import io.spine.message.delivery.rejection.UnableToReleaseShard;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.Apply;
import io.spine.server.command.Assign;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.WorkerId;

import static io.spine.message.delivery.rejection.Rejections.UnableToReleaseShard.Reason;
import static io.spine.protobuf.Messages.isDefault;
import static java.lang.String.format;

/**
 * Guards shard delivery sessions.
 *
 * <p>The registry keeps track of the shard workers and prevents the shard from being picked up
 * by more than one worker at time.
 */
final class SessionRegistry
        extends Aggregate<ShardIndex, ShardSessionRegistry, ShardSessionRegistry.Builder>
        implements Logging {

    @Assign
    ShardPickedUp handle(PickUpShard c, CommandContext context) throws ShardAlreadyPickedUp {
        var shard = c.getShard();
        var worker = c.getWorker();
        _debug().log("Worker `%s` is picking up shard `%s`.", worker, shard);
        checkNotPickedUp();
        _info().log("Shard `%s` is picked up by worker `%s`.", shard, worker);
        return ShardPickedUp.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .setWhenPicked(Time.currentTime())
                .vBuild();
    }

    private void checkNotPickedUp() throws ShardAlreadyPickedUp {
        var state = state();
        if (state.hasWorker()) {
            var shard = id();
            var worker = state.getWorker();
            _debug().log("Shard `%s` is already picked up by `%s`.", shard, worker);
            throw ShardAlreadyPickedUp.newBuilder()
                    .setShard(shard)
                    .setWorker(worker)
                    .build();
        }
    }

    @Apply
    private void on(ShardPickedUp e) {
        builder().setWorker(e.getWorker())
                 .setWhenPicked(e.getWhenPicked())
                 .setWhenLastPicked(e.getWhenPicked());
    }

    @Assign
    ShardReleased handle(ReleaseShard c, CommandContext context) throws UnableToReleaseShard {
        var shard = c.getShard();
        var worker = c.getWorker();
        _debug().log("Worker `%s` is releasing shard `%s`.", worker, shard);
        checkCanRelease(c);
        _info().log("Shard `%s` is released by worker `%s`.", shard, worker);
        return ShardReleased.newBuilder()
                .setShard(shard)
                .setWorker(worker)
                .setWhenReleased(Time.currentTime())
                .vBuild();
    }

    private void checkCanRelease(ReleaseShard c) throws UnableToReleaseShard {
        var state = state();
        var currentWorker = state.getWorker();
        if (isDefault(currentWorker)) {
            _debug().log("Shard `%s` is not picked up. Nothing to release.", id());
            throw unableToRelease(c, shardNotPickedUp());
        }
        var workerToPickUpShard = c.getWorker();
        if (!currentWorker.equals(workerToPickUpShard)) {
            _debug().log("Worker `%s` cannot release a shard `%s` picked up by `%s`.",
                         workerToPickUpShard, id(), currentWorker);
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
    static Reason shardPickedUpByOtherWorker(WorkerId currentWorker) {
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
        builder().clearWorker()
                 .clearWhenPicked();
    }
}
