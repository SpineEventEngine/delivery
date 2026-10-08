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

package io.spine.delivery.storage.memory

import io.spine.delivery.storage.CasOutcome
import io.spine.delivery.storage.ChangeListeners
import io.spine.delivery.storage.ShardSessionStore
import io.spine.delivery.storage.Stored
import io.spine.delivery.storage.Subscription
import io.spine.server.delivery.ShardIndex
import io.spine.server.delivery.ShardSessionRecord
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

/**
 * A [ShardSessionStore] that keeps the records in the memory of the process.
 *
 * The stored form of a record is the record instance itself, so [compareAndSet]
 * compares records with `equals`.
 */
public class InMemoryShardSessionStore : ShardSessionStore {

    private val records = ConcurrentHashMap<ShardIndex, ShardSessionRecord>()
    private val listeners = ChangeListeners()

    override fun read(shard: ShardIndex): Stored? = records[shard]?.let(::stored)

    override fun read(shards: Collection<ShardIndex>): Map<ShardIndex, Stored> {
        val result = HashMap<ShardIndex, Stored>()
        for (shard in shards) {
            records[shard]?.let { result[shard] = stored(it) }
        }
        return result
    }

    override fun readAll(): List<Stored> = records.values.map(::stored)

    override fun compareAndSet(
        shard: ShardIndex,
        expected: Stored?,
        replacement: ShardSessionRecord
    ): CasOutcome {
        var conflict: CasOutcome.Conflict? = null
        records.compute(shard) { _, current ->
            if (current == expected?.record) {
                replacement
            } else {
                conflict = CasOutcome.Conflict(current?.let(::stored))
                current
            }
        }
        val outcome = conflict ?: CasOutcome.Applied
        if (outcome == CasOutcome.Applied) {
            listeners.changed(shard)
        }
        return outcome
    }

    override fun subscribe(onChange: Consumer<ShardIndex>): Subscription = listeners.add(onChange)

    /**
     * Removes the listeners. The records stay readable.
     */
    override fun close() {
        listeners.clear()
    }

    private fun stored(record: ShardSessionRecord) = Stored(record, record)
}
