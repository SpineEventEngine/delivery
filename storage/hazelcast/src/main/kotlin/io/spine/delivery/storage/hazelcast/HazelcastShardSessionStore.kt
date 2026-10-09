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

package io.spine.delivery.storage.hazelcast

import com.hazelcast.map.IMap
import io.spine.delivery.storage.CasOutcome
import io.spine.delivery.storage.ChangeListeners
import io.spine.delivery.storage.MissedChangeListeners
import io.spine.delivery.storage.ShardSessionStore
import io.spine.delivery.storage.Stored
import io.spine.delivery.storage.Subscription
import io.spine.delivery.storage.parseSession
import io.spine.delivery.storage.shardOf
import io.spine.delivery.storage.tag
import io.spine.server.delivery.ShardIndex
import io.spine.server.delivery.ShardSessionRecord
import java.util.function.Consumer

/**
 * A [ShardSessionStore] kept in a Hazelcast map, from the [tag][tag] of a shard to the bytes
 * of its session record.
 *
 * The map keeps its values as bytes, so its `replace` and `putIfAbsent` compare the stored
 * bytes, which makes them the atomic compare-and-set that [compareAndSet] needs.
 *
 * @param map The map of the records.
 * @param missed The listeners of the changes that the store may have missed, shared with
 *   the other store of the same member.
 */
public class HazelcastShardSessionStore internal constructor(
    private val map: IMap<String, ByteArray>,
    private val missed: MissedChangeListeners
) : ShardSessionStore {

    /**
     * The listeners passed to [subscribe].
     */
    private val listeners = ChangeListeners()

    /**
     * The ID of the listener of the changes of [map], which tells [listeners] about them.
     *
     * The listener receives the changes made by every member of the cluster, without
     * the records themselves.
     */
    private val listenerId = map.addEntryListener(ShardChanges(listeners), false)

    override fun read(shard: ShardIndex): Stored? = map[shard.tag()]?.let(::stored)

    /**
     * Reads the records with one `getAll` of the map.
     */
    override fun read(shards: Collection<ShardIndex>): Map<ShardIndex, Stored> {
        if (shards.isEmpty()) {
            return emptyMap()
        }
        val found = map.getAll(shards.mapTo(HashSet()) { it.tag() })
        val result = HashMap<ShardIndex, Stored>()
        for ((tag, bytes) in found) {
            result[shardOf(tag)] = stored(bytes)
        }
        return result
    }

    override fun readAll(): List<Stored> = map.values.map(::stored)

    /**
     * Writes with `putIfAbsent` if no record is expected, and with `replace` of the expected
     * bytes otherwise. Both compare the stored bytes and write in one atomic step.
     *
     * When `replace` fails, the current record is read again, so the conflict may report
     * a record newer than the one that failed the comparison.
     *
     * @throws IllegalArgumentException If [expected] was not read from a store that keeps
     *   records as bytes.
     */
    override fun compareAndSet(
        shard: ShardIndex,
        expected: Stored?,
        replacement: ShardSessionRecord
    ): CasOutcome {
        val tag = shard.tag()
        val bytes = replacement.toByteArray()
        if (expected == null) {
            val current = map.putIfAbsent(tag, bytes) ?: return CasOutcome.Applied
            return CasOutcome.Conflict(stored(current))
        }
        val form = expected.form
        require(form is ByteArray) { "The expected record was not read from this store." }
        if (map.replace(tag, form, bytes)) {
            return CasOutcome.Applied
        }
        return CasOutcome.Conflict(map[tag]?.let(::stored))
    }

    /**
     * The listener is called on a thread of Hazelcast, for the records written by every
     * member.
     */
    override fun subscribe(onChange: Consumer<ShardIndex>): Subscription = listeners.add(onChange)

    /**
     * Calls [onMissed] when a member leaves the cluster, when a part of the data is lost,
     * and when the cluster joins again after a network split.
     *
     * Hazelcast may lose the reports of the changes in these cases.
     */
    override fun subscribeToMissedChanges(onMissed: Runnable): Subscription = missed.add(onMissed)

    /**
     * Stops listening to the changes of the map, and removes the listeners passed to
     * [subscribe].
     *
     * The records stay in the cluster.
     */
    override fun close() {
        try {
            map.removeEntryListener(listenerId)
        } finally {
            listeners.clear()
        }
    }

    /**
     * Returns the record with the given bytes as its stored form.
     */
    private fun stored(bytes: ByteArray) = Stored(parseSession(bytes), bytes)
}
