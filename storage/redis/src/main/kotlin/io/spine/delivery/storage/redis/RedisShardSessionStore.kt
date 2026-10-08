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

package io.spine.delivery.storage.redis

import io.spine.delivery.storage.CasOutcome
import io.spine.delivery.storage.ChangeListeners
import io.spine.delivery.storage.ShardSessionStore
import io.spine.delivery.storage.Stored
import io.spine.delivery.storage.Subscription
import io.spine.delivery.storage.shardOf
import io.spine.delivery.storage.tag
import io.spine.server.delivery.ShardIndex
import io.spine.server.delivery.ShardSessionRecord
import java.util.function.Consumer
import org.redisson.api.RScript.Mode.READ_WRITE
import org.redisson.api.RedissonClient

/**
 * A [ShardSessionStore] kept in one Redis hash, from the shard tag to the record bytes.
 *
 * [compareAndSet] runs as a Lua script that compares the stored bytes.
 */
public class RedisShardSessionStore internal constructor(
    client: RedissonClient
) : ShardSessionStore {

    private val hash = client.getMap<String, ByteArray>(SESSIONS_KEY, HASH_CODEC)
    private val listeners = ChangeListeners()
    private val channel =
        ChangeChannel(client, SESSIONS_CHANNEL) { listeners.changed(shardOf(it)) }
    private val compareAndSetScript = LuaScript(client, COMPARE_AND_SET_SCRIPT)

    override fun read(shard: ShardIndex): Stored? = hash[shard.tag()]?.let(::stored)

    override fun read(shards: Collection<ShardIndex>): Map<ShardIndex, Stored> {
        if (shards.isEmpty()) {
            return emptyMap()
        }
        val found = hash.getAll(shards.mapTo(HashSet()) { it.tag() })
        val result = HashMap<ShardIndex, Stored>()
        for ((tag, bytes) in found) {
            result[shardOf(tag)] = stored(bytes)
        }
        return result
    }

    override fun readAll(): List<Stored> = hash.readAllValues().map(::stored)

    override fun compareAndSet(
        shard: ShardIndex,
        expected: Stored?,
        replacement: ShardSessionRecord
    ): CasOutcome {
        val expectedBytes = if (expected == null) {
            ByteArray(0)
        } else {
            val form = expected.form
            require(form is ByteArray) { "The expected record was not read from this store." }
            form
        }
        val result = compareAndSetScript.run(
            READ_WRITE,
            listOf(SESSIONS_KEY),
            listOf(
                shard.tag().toByteArray(),
                (if (expected == null) "0" else "1").toByteArray(),
                expectedBytes,
                replacement.toByteArray()
            )
        )
        if (result.first() == 1L) {
            return CasOutcome.Applied
        }
        return CasOutcome.Conflict((result.getOrNull(1) as ByteArray?)?.let(::stored))
    }

    override fun subscribe(onChange: Consumer<ShardIndex>): Subscription = listeners.add(onChange)

    override fun subscribeToMissedChanges(onMissed: Runnable): Subscription =
        channel.subscribeToMissed(onMissed)

    override fun close() {
        channel.close()
        listeners.clear()
    }

    private fun stored(bytes: ByteArray) = Stored(ShardSessionRecord.parseFrom(bytes), bytes)
}
