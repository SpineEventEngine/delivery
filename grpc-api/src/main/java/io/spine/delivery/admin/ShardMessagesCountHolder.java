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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.logging.WithLogging;
import io.spine.server.delivery.InboxMessageId;
import io.spine.server.delivery.ShardIndex;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Maps a {@code ShardIndex} to the number of messages currently available in the shard.
 *
 * <p>The number is derived from the identifiers of the messages known to be in the shard,
 * rather than accumulated from a running counter. This makes the accounting idempotent:
 * the storage reports an overwrite of a stored message as an ordinary write, and a repeated
 * removal of the same message as an ordinary delete, but neither changes the count.
 * See {@link #messageWritten(InboxMessageId)} and {@link #messageRemoved(InboxMessageId)}.
 *
 * <p>Keeping the identifiers is faster than fetching the counts on demand, because the storage
 * does not support {@code count} queries, so fetching basically means reading all the messages
 * and counting them. The memory cost is proportional to the number of messages currently
 * stored in the inbox: once a shard is drained, the memory taken by its identifiers is released.
 *
 * <p>Within a shard, a message is identified by the UUID of its {@code InboxMessageId} alone,
 * the shard index being the same for all of them. Keeping the UUID string, rather than the whole
 * identifier message, takes about 40% less memory per message and makes updates slightly faster.
 *
 * <p>Each shard is updated under its own lock, so the count returned by an update reflects
 * exactly the messages in the shard at the moment of the update. Updates of different shards
 * do not block each other.
 *
 * <p>The updates of one message are expected in the order of the storage operations:
 * a removal recorded before the write of the same message is ignored, and the message stays
 * counted until the server restarts. The storage reports an operation on the thread
 * performing it, so this requires a concurrent write and removal of the same message.
 */
@ThreadSafe
public final class ShardMessagesCountHolder implements WithLogging {

    private final ConcurrentMap<ShardIndex, ShardMessages> messagesInShards =
            new ConcurrentHashMap<>();

    /**
     * Records that the message with the given {@code id} is stored in its shard, and returns
     * the resulting number of messages in the shard.
     *
     * <p>Recording a message that is already known — as happens when a stored message
     * is overwritten — leaves the count unchanged.
     */
    @CanIgnoreReturnValue
    public int messageWritten(InboxMessageId id) {
        checkNotNull(id);
        var messages =
                messagesInShards.computeIfAbsent(id.getIndex(), index -> new ShardMessages());
        return messages.add(id.getUuid());
    }

    /**
     * Records that the message with the given {@code id} is no longer stored in its shard,
     * and returns the resulting number of messages in the shard.
     *
     * <p>Recording the removal of an unknown message — as happens when the same message
     * is removed twice, e.g. by a client retrying the removal after a transport error —
     * leaves the count unchanged, so it never becomes negative.
     */
    @CanIgnoreReturnValue
    public int messageRemoved(InboxMessageId id) {
        checkNotNull(id);
        var messages = messagesInShards.get(id.getIndex());
        if (messages == null) {
            return 0;
        }
        return messages.remove(id.getUuid());
    }

    /**
     * Creates and returns a new mutable map from each shard index to the number of messages
     * in that shard.
     *
     * <p>Changes in the returned map don't affect this holder.
     */
    public Map<ShardIndex, Integer> toMutableMap() {
        Map<ShardIndex, Integer> result = new HashMap<>();
        messagesInShards.forEach((index, messages) -> result.put(index, messages.count()));
        return result;
    }

    /**
     * The UUIDs of the messages known to be in one shard.
     *
     * <p>The methods are synchronized so that an update and the count it returns
     * are atomic with respect to the other updates of the same shard.
     */
    private static final class ShardMessages {

        @GuardedBy("this")
        private Set<String> uuids = new HashSet<>();

        /**
         * Adds the given {@code uuid} unless it is already known, and returns the resulting
         * number of messages.
         */
        private synchronized int add(String uuid) {
            uuids.add(uuid);
            return uuids.size();
        }

        /**
         * Removes the given {@code uuid} if it is known, and returns the resulting
         * number of messages.
         *
         * <p>A removal that empties the set replaces it with a new one. A {@code HashSet} never
         * shrinks its backing table, so a drained shard would otherwise keep a table sized for
         * its largest backlog for as long as the server runs.
         */
        private synchronized int remove(String uuid) {
            if (uuids.remove(uuid) && uuids.isEmpty()) {
                uuids = new HashSet<>();
            }
            return uuids.size();
        }

        /**
         * Returns the number of messages.
         */
        private synchronized int count() {
            return uuids.size();
        }
    }
}
