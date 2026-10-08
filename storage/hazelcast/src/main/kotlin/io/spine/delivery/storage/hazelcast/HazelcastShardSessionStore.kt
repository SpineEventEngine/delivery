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
 * A [ShardSessionStore] kept in the Hazelcast map [SESSIONS_MAP] of serialized records,
 * keyed by the shard tag.
 *
 * The map keeps its values in the binary form, so `replace` and `putIfAbsent` compare
 * the stored bytes.
 */
public class HazelcastShardSessionStore internal constructor(
    private val map: IMap<String, ByteArray>,
    private val missed: MissedChangeListeners
) : ShardSessionStore {

    private val listeners = ChangeListeners()
    private val listenerId = map.addEntryListener(ShardChanges(listeners), false)

    override fun read(shard: ShardIndex): Stored? = map[shard.tag()]?.let(::stored)

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

    override fun subscribe(onChange: Consumer<ShardIndex>): Subscription = listeners.add(onChange)

    override fun subscribeToMissedChanges(onMissed: Runnable): Subscription = missed.add(onMissed)

    override fun close() {
        try {
            map.removeEntryListener(listenerId)
        } finally {
            listeners.clear()
        }
    }

    private fun stored(bytes: ByteArray) = Stored(parseSession(bytes), bytes)
}
