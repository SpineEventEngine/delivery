/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.spine.delivery.admin;

import io.spine.logging.WithLogging;
import io.spine.server.delivery.ShardIndex;

import javax.annotation.concurrent.ThreadSafe;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps a {@code ShardIndex} to the number of messages currently available in the shard.
 *
 * <p>Accumulating the number is faster than fetching it on demand, because storage doesn't
 * support {@code count} queries, so fetching basically means read all the shards and then read
 * all the messages in each shard to count the number.
 *
 * <p>The {@code ConcurrentHashMap} is chosen because we want to protect write operations and
 * do not block read operations.
 */
@ThreadSafe
public final class ShardMessagesCountHolder implements WithLogging {

    private final ConcurrentHashMap<ShardIndex, Integer> messagesInShards;

    /**
     * Creates a new {@code ShardMessagesHolder}.
     */
    public ShardMessagesCountHolder() {
        this(new ConcurrentHashMap<>());
    }

    /**
     * Creates a new {@code ShardMessagesHolder} and fills it with the given {@code initial}
     * mapping.
     */
    public ShardMessagesCountHolder(Map<ShardIndex, Integer> initial) {
        messagesInShards = new ConcurrentHashMap<>(initial);
    }

    /**
     * Updates the {@code messagesInShards} for the given {@code index} on the given {@code delta}.
     *
     * <p>In the implementation we rely on the fact that the {@code merge()}
     * operation is atomic in the {@code ConcurrentHashMap}. If one update of the map
     * is in progress, other updates will be postponed by the time when
     * the first update passes.
     *
     * <p>In some rare cases if the {@code delta} is {@code -1} (message removed) and the
     * map doesn't contain any info about the shard with the {@code index}, the count will
     * become {@code -1}. This means that we have events misordering and
     * the “MessageRemoved” update arrived earlier than the “MessageWritten”.
     * That's why we don't force the count to be always positive, hoping that
     * the “MessageWritten” will arrive shortly and will make
     * the state consistent — ({@code 0}).
     */
    public int updateCount(ShardIndex index, int delta) {
        return messagesInShards.merge(index, delta, Integer::sum);
    }

    /**
     * Creates and returns a new mutable copy of the underlying mapping.
     *
     * <p>Changes in the returned map don't affect the original mapping.
     */
    public Map<ShardIndex, Integer> toMutableMap() {
        return new HashMap<>(messagesInShards);
    }
}
