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

import io.spine.delivery.admin.grpc.ShardInfoUpdate;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.ShardSessionRecord;
import org.jspecify.annotations.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
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
     * Creates a new {@code ShardInfoUpdate} with the full current state of the shard: its
     * status, the time it was last picked, and the number of its messages.
     *
     * <p>A shard without a session record has never been picked: its status is
     * {@code NOT_PICKED}, and the time of the last pick is not set.
     *
     * @param index
     *         the index of the shard
     * @param session
     *         the session record of the shard, or {@code null} if there is none
     * @param messagesCount
     *         the number of messages in the shard
     */
    public static ShardInfoUpdate currentState(ShardIndex index,
                                               @Nullable ShardSessionRecord session,
                                               int messagesCount) {
        checkNotDefaultArg(index);
        checkArgument(messagesCount >= 0,
                      "The number of messages cannot be negative, but was %s.", messagesCount);
        var update = ShardInfoUpdate.newBuilder()
                .setIndex(index)
                .setNewStatus(NOT_PICKED)
                .setNewMessagesCount(messagesCount);
        if (session != null) {
            update.setNewStatus(session.hasWorker() ? PICKED : NOT_PICKED);
            if (session.hasWhenLastPicked()) {
                update.setWhenLastPicked(session.getWhenLastPicked());
            }
        }
        return update.build();
    }
}
