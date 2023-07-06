/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.admin;

import com.google.protobuf.Timestamp;
import io.spine.message.delivery.admin.grpc.ShardInfoUpdate;
import io.spine.server.delivery.ShardIndex;

import static com.google.common.base.Preconditions.checkArgument;
import static io.spine.message.delivery.admin.grpc.ShardStatus.NOT_PICKED;
import static io.spine.message.delivery.admin.grpc.ShardStatus.PICKED;
import static io.spine.util.Preconditions2.checkNotDefaultArg;

/**
 * Utility to create {@link ShardInfoUpdate}s.
 */
public final class ShardInfoUpdates {

    private ShardInfoUpdates() {
    }

    /**
     * Creates a new {@code ShardInfoUpdate} with the given shard {@code index} and
     * {@code lastPicked} time, and shard status changed to {@code PICKED}.
     */
    public static ShardInfoUpdate shardPicked(ShardIndex index, Timestamp lastPicked) {
        checkNotDefaultArg(lastPicked);
        return changesFor(index)
                .setNewStatus(PICKED)
                .setWhenLastPicked(lastPicked)
                .vBuild();
    }

    /**
     * Creates a new {@code ShardInfoUpdate} with the given shard {@code index} and shard status
     * changed to {@code NOT_PICKED}.
     */
    public static ShardInfoUpdate shardUnpicked(ShardIndex index) {
        checkNotDefaultArg(index);
        return changesFor(index)
                .setNewStatus(NOT_PICKED)
                .vBuild();
    }

    /**
     * Creates a new {@code ShardInfoUpdate} with the given shard {@code index} and
     * the new {@code count} of messages in the shard.
     *
     * <p>We intentionally do not force the argument to be positive because in some cases it may
     * be negative for a short period of time. For more info see the
     * {@linkplain io.spine.message.delivery.admin.ShardMessagesCountHolder#updateCount(ShardIndex,
     * int)} method documentation, where the counter is updated.
     */
    public static ShardInfoUpdate messagesCountChangedTo(ShardIndex index, int count) {
        checkNotDefaultArg(index);
        return changesFor(index)
                .setNewMessagesCount(count)
                .vBuild();
    }

    /**
     * Creates a new {@code ShardInfoUpdate.Builder} with the given shard {@code index} set.
     */
    private static ShardInfoUpdate.Builder changesFor(ShardIndex index) {
        checkNotDefaultArg(index);
        return ShardInfoUpdate
                .newBuilder()
                .setIndex(index);
    }
}
