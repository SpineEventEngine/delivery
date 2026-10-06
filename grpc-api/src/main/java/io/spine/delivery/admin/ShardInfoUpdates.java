/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package io.spine.delivery.admin;

import com.google.protobuf.Timestamp;
import io.spine.delivery.admin.grpc.ShardInfoUpdate;
import io.spine.server.delivery.ShardIndex;

import static io.spine.delivery.admin.grpc.ShardStatus.NOT_PICKED;
import static io.spine.delivery.admin.grpc.ShardStatus.PICKED;
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
                .build();
    }

    /**
     * Creates a new {@code ShardInfoUpdate} with the given shard {@code index} and shard status
     * changed to {@code NOT_PICKED}.
     */
    public static ShardInfoUpdate shardUnpicked(ShardIndex index) {
        checkNotDefaultArg(index);
        return changesFor(index)
                .setNewStatus(NOT_PICKED)
                .build();
    }

    /**
     * Creates a new {@code ShardInfoUpdate} with the given shard {@code index} and
     * the new {@code count} of messages in the shard.
     *
     * <p>The {@code count} is expected to be non-negative but is intentionally not validated:
     * the update is created after the storage operation it reports has succeeded, and
     * rejecting the value would turn a completed operation into a failed call.
     */
    public static ShardInfoUpdate messagesCountChangedTo(ShardIndex index, int count) {
        checkNotDefaultArg(index);
        return changesFor(index)
                .setNewMessagesCount(count)
                .build();
    }

    /**
     * Creates a new {@code ShardInfoUpdate.Builder} with the given shard {@code index} set.
     */
    private static ShardInfoUpdate.Builder changesFor(ShardIndex index) {
        checkNotDefaultArg(index);
        return ShardInfoUpdate.newBuilder()
                .setIndex(index);
    }
}
