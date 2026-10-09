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

package io.spine.delivery.server.given

import io.spine.delivery.storage.ShardSessionStore
import io.spine.delivery.storage.Stored
import io.spine.server.delivery.ShardIndex
import java.util.concurrent.ConcurrentHashMap

/**
 * A session store whose records can vanish without a notification, as the records of
 * a lost part of the data of a Hazelcast cluster do.
 *
 * @param delegate The store that keeps the records.
 */
internal class VanishingShardSessionStore(
    private val delegate: ShardSessionStore
) : ShardSessionStore by delegate {

    /**
     * The shards whose records are hidden from all reads.
     */
    private val vanished: MutableSet<ShardIndex> = ConcurrentHashMap.newKeySet()

    /**
     * Makes the record of the shard vanish from all reads.
     */
    fun vanish(shard: ShardIndex) {
        vanished.add(shard)
    }

    override fun read(shard: ShardIndex): Stored? =
        if (shard in vanished) null else delegate.read(shard)

    override fun read(shards: Collection<ShardIndex>): Map<ShardIndex, Stored> =
        delegate.read(shards).filterKeys { it !in vanished }

    override fun readAll(): List<Stored> =
        delegate.readAll().filter { it.record.index !in vanished }
}
