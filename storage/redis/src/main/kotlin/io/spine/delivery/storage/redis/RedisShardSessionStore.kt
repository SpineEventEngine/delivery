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
import io.spine.delivery.storage.parseSession
import io.spine.delivery.storage.sessionForm
import io.spine.delivery.storage.shardOf
import io.spine.delivery.storage.tag
import io.spine.server.delivery.ShardIndex
import io.spine.server.delivery.ShardSessionRecord
import java.util.UUID
import java.util.function.Consumer
import org.redisson.api.RScript.Mode.READ_WRITE
import org.redisson.api.RedissonClient

/**
 * A [ShardSessionStore] kept in one Redis hash, from the [tag] of a shard to
 * the [stored form][sessionForm] of its session record.
 *
 * @param client The client connected to Redis.
 */
public class RedisShardSessionStore internal constructor(
    client: RedissonClient
) : ShardSessionStore {

    /**
     * The hash of the records.
     */
    private val hash = client.getMap<String, ByteArray>(SESSIONS_KEY, HASH_CODEC)

    /**
     * The listeners passed to [subscribe].
     */
    private val listeners = ChangeListeners()

    /**
     * The channel on which [COMPARE_AND_SET_SCRIPT] publishes the shards of the written
     * records, which tells [listeners] about them.
     */
    private val channel =
        ChangeChannel(client, SESSIONS_CHANNEL) { listeners.changed(shardOf(it)) }

    /**
     * Writes a record if the stored one is the expected one.
     */
    private val compareAndSetScript = LuaScript(client, COMPARE_AND_SET_SCRIPT)

    override fun read(shard: ShardIndex): Stored? = hash[shard.tag()]?.let(::parseSession)

    /**
     * Reads the records in one round trip.
     */
    override fun read(shards: Collection<ShardIndex>): Map<ShardIndex, Stored> {
        if (shards.isEmpty()) {
            return emptyMap()
        }
        val found = hash.getAll(shards.mapTo(HashSet()) { it.tag() })
        val result = HashMap<ShardIndex, Stored>()
        for ((tag, bytes) in found) {
            result[shardOf(tag)] = parseSession(bytes)
        }
        return result
    }

    override fun readAll(): List<Stored> = hash.readAllValues().map(::parseSession)

    /**
     * Compares the stored bytes with those of [expected], and writes the replacement, with
     * [COMPARE_AND_SET_SCRIPT], in one atomic step of Redis.
     *
     * @throws IllegalArgumentException If [expected] was not read from a store that keeps
     *   records as bytes.
     */
    override fun compareAndSet(
        shard: ShardIndex,
        expected: Stored?,
        replacement: ShardSessionRecord,
        writeId: UUID
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
                sessionForm(replacement, writeId)
            )
        )
        val applied = result.firstOrNull() ?: error("The compare-and-set script returned nothing.")
        if (applied == 1L) {
            return CasOutcome.Applied
        }
        return CasOutcome.Conflict((result.getOrNull(1) as ByteArray?)?.let(::parseSession))
    }

    /**
     * The listener is called on a thread of the Redis client, for the records written by
     * every process connected to the same Redis database.
     */
    override fun subscribe(onChange: Consumer<ShardIndex>): Subscription = listeners.add(onChange)

    /**
     * Calls [onMissed] whenever the subscription to the channel of the changes is
     * established, for the first time or again, as Redis does not keep the messages
     * published while a subscriber is disconnected.
     */
    override fun subscribeToMissedChanges(onMissed: Runnable): Subscription =
        channel.subscribeToMissed(onMissed)

    /**
     * Stops listening to the channel of the changes, and removes the listeners passed to
     * [subscribe].
     *
     * The records stay in Redis, and the client stays connected.
     */
    override fun close() {
        try {
            channel.close()
        } finally {
            listeners.clear()
        }
    }
}
