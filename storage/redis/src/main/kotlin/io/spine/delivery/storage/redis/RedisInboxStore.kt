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

import com.google.protobuf.Timestamp
import io.spine.delivery.storage.ChangeListeners
import io.spine.delivery.storage.InboxStore
import io.spine.delivery.storage.Subscription
import io.spine.delivery.storage.checkPageSize
import io.spine.delivery.storage.parseMessage
import io.spine.delivery.storage.shardOf
import io.spine.delivery.storage.tag
import io.spine.logging.WithLogging
import io.spine.server.delivery.InboxMessage
import io.spine.server.delivery.InboxMessageId
import io.spine.server.delivery.InboxMessageStatus.TO_DELIVER
import io.spine.server.delivery.ShardIndex
import java.util.function.Consumer
import org.redisson.api.RScript.Mode.READ_ONLY
import org.redisson.api.RScript.Mode.READ_WRITE
import org.redisson.api.RedissonClient
import org.redisson.api.options.KeysScanOptions

/**
 * The most messages that one run of a script writes or removes.
 */
private const val MAX_CHUNK_MESSAGES = 1_000

/**
 * The most bytes of messages that one run of a script writes, unless a single message
 * is larger.
 */
private const val MAX_CHUNK_BYTES = 8 * 1024 * 1024

/**
 * The number of keys that Redis checks per step when it looks for the keys of the shards.
 */
private const val SCAN_COUNT = 1_000

/**
 * An [InboxStore] kept in Redis.
 *
 * Each shard has four keys, named after the [tag] of the shard:
 *  - a [hash of its messages][messagesKey], from the UUID of each message to its bytes;
 *  - a [hash of their order keys][orderKeysKey], from the UUID of each message to its
 *    order key, encoded as a string that sorts as the key does;
 *  - a [sorted set of all its messages][allKey], whose elements are the encoded order keys
 *    followed by the UUIDs, so that Redis keeps them in the order of the order keys;
 *  - a [sorted set of its messages to deliver][pendingKey], built the same way.
 *
 * A write or a removal of the messages of a shard runs as a Lua script, which Redis runs
 * as one atomic step. A large batch is split into several runs, of at most 1,000 messages
 * and 8 MiB of bytes each. A script that fails midway keeps the writes it has made, so all
 * the arguments of a batch are prepared before its first run.
 *
 * Pages and the newest message to deliver are read by scripts too, each in one round trip
 * to Redis.
 *
 * @param client The client connected to Redis.
 */
public class RedisInboxStore internal constructor(
    private val client: RedissonClient
) : InboxStore, WithLogging {

    /**
     * The listeners passed to [subscribe].
     */
    private val listeners = ChangeListeners()

    /**
     * The channel on which the scripts publish the changed shards, which tells [listeners]
     * about them.
     */
    private val channel = ChangeChannel(client, INBOX_CHANNEL) { listeners.changed(shardOf(it)) }

    /**
     * Writes messages of a shard.
     */
    private val writeScript = LuaScript(client, WRITE_SCRIPT)

    /**
     * Removes messages of a shard.
     */
    private val deleteScript = LuaScript(client, DELETE_SCRIPT)

    /**
     * Reads a page of the messages of a shard.
     */
    private val pageScript = LuaScript(client, PAGE_SCRIPT)

    /**
     * Finds the newest message to deliver in a shard.
     */
    private val newestScript = LuaScript(client, NEWEST_SCRIPT)

    /**
     * Writes the messages of each shard with [WRITE_SCRIPT], in runs of at most 1,000
     * messages and 8 MiB of bytes.
     */
    override fun write(messages: Iterable<InboxMessage>) {
        val prepared = messages.groupBy { it.id.index.tag() }
            .mapValues { (_, batch) -> batch.map(::prepare) }
        for ((tag, batch) in prepared) {
            for (chunk in chunks(batch)) {
                val args =
                    ArrayList<ByteArray>(WRITE_HEADER_ARGS + chunk.size * WRITE_ARGS_PER_MESSAGE)
                args.add(tag.toByteArray())
                args.add(chunk.size.toString().toByteArray())
                chunk.forEach { args.addAll(it) }
                writeScript.run(READ_WRITE, shardKeys(tag), args)
            }
        }
    }

    /**
     * Removes the messages of each shard with [DELETE_SCRIPT], in runs of at most 1,000
     * messages.
     */
    override fun delete(ids: Iterable<InboxMessageId>) {
        val prepared = ids.groupBy { it.index.tag() }
            .mapValues { (_, batch) -> batch.map { it.uuid.toByteArray() } }
        for ((tag, uuids) in prepared) {
            for (chunk in uuids.chunked(MAX_CHUNK_MESSAGES)) {
                deleteScript.run(READ_WRITE, shardKeys(tag), listOf(tag.toByteArray()) + chunk)
            }
        }
    }

    /**
     * Reads the bytes of the message from the hash of the messages of its shard, without
     * a script.
     */
    override fun find(id: InboxMessageId): InboxMessage? =
        client.getMap<String, ByteArray>(messagesKey(id.index.tag()), HASH_CODEC)[id.uuid]
            ?.let(::parseMessage)

    /**
     * Reads the page with [PAGE_SCRIPT]. Logs a warning if an element of the sorted set has
     * no message.
     */
    override fun page(shard: ShardIndex, since: Timestamp?, pageSize: Int): List<InboxMessage> {
        checkPageSize(pageSize)
        val tag = shard.tag()
        val lowerBound = if (since == null) "-" else "[${encodeTime(since.seconds, since.nanos)};"
        val result = pageScript.run(
            READ_ONLY,
            listOf(messagesKey(tag), allKey(tag)),
            listOf(lowerBound.toByteArray(), pageSize.toString().toByteArray())
        )
        reportMissing(tag, result)
        return result.drop(1).map { parseMessage(it as ByteArray) }
    }

    /**
     * Finds the message with [NEWEST_SCRIPT]. Logs a warning if an element of the sorted set
     * has no message.
     */
    override fun newestToDeliver(shard: ShardIndex): InboxMessage? {
        val tag = shard.tag()
        val result = newestScript.run(
            READ_ONLY, listOf(messagesKey(tag), pendingKey(tag)), emptyList()
        )
        reportMissing(tag, result)
        return (result.getOrNull(1) as ByteArray?)?.let(::parseMessage)
    }

    /**
     * Returns the size of the hash of the messages of the shard.
     */
    override fun count(shard: ShardIndex): Int =
        client.getMap<String, ByteArray>(messagesKey(shard.tag()), HASH_CODEC).size

    /**
     * Reads the sizes of the hashes of the messages of all the shards in one round trip.
     */
    override fun count(shards: Collection<ShardIndex>): Map<ShardIndex, Int> =
        countAll(shards.associateBy { messagesKey(it.tag()) })

    /**
     * Finds the hashes of the messages of all the shards, and reads their sizes in one round
     * trip.
     *
     * Finding the hashes walks every key of the Redis database, so it costs in proportion to
     * the number of all the keys, not only those of this store. Redis removes an empty hash,
     * so a shard without messages has no hash.
     */
    override fun counts(): Map<ShardIndex, Int> {
        val scan = KeysScanOptions.defaults()
            .pattern(MESSAGES_PATTERN)
            .chunkSize(SCAN_COUNT)
        val shardsByKey = client.keys.getKeys(scan)
            .associateWith { shardOf(tagOfMessagesKey(it)) }
        return countAll(shardsByKey).filterValues { it > 0 }
    }

    /**
     * Returns the sizes of the given hashes of messages, read in one round trip.
     *
     * @param shardsByKey The shards by the keys of their hashes of messages.
     */
    private fun countAll(shardsByKey: Map<String, ShardIndex>): Map<ShardIndex, Int> {
        if (shardsByKey.isEmpty()) {
            return emptyMap()
        }
        val batch = client.createBatch()
        val sizes = shardsByKey.mapValues { (key, _) ->
            batch.getMap<String, ByteArray>(key, HASH_CODEC).sizeAsync()
        }
        batch.execute()
        val result = HashMap<ShardIndex, Int>()
        for ((key, shard) in shardsByKey) {
            result[shard] = sizes.getValue(key).toCompletableFuture().join()
        }
        return result
    }

    /**
     * The listener is called on a thread of the Redis client, for the changes made by every
     * process connected to the same Redis database.
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
     * The data stays in Redis, and the client stays connected.
     */
    override fun close() {
        try {
            channel.close()
        } finally {
            listeners.clear()
        }
    }

    /**
     * Logs a warning if the result of a reading script reports elements without a message.
     *
     * @param tag The tag of the shard that the script read.
     * @param result The result of the script, which starts with the number of such elements.
     */
    private fun reportMissing(tag: String, result: List<Any?>) {
        val missing = result.firstOrNull() as Long? ?: 0L
        if (missing > 0) {
            logger.atWarning().log {
                "$missing messages of the shard `$tag` are listed without a value, and skipped."
            }
        }
    }
}

/**
 * Returns the keys of a shard, in the order that [WRITE_SCRIPT] and [DELETE_SCRIPT] expect.
 */
private fun shardKeys(tag: String): List<String> =
    listOf(messagesKey(tag), orderKeysKey(tag), allKey(tag), pendingKey(tag))

/**
 * Returns the arguments of [WRITE_SCRIPT] for a message: its UUID, its encoded order key,
 * whether it is to be delivered, and its bytes.
 */
private fun prepare(message: InboxMessage): List<ByteArray> {
    val received = message.whenReceived
    val key = encodeOrderKey(
        seconds = received.seconds,
        nanos = received.nanos,
        version = message.version
    )
    val toDeliver = if (message.status == TO_DELIVER) "1" else "0"
    return listOf(
        message.id.uuid.toByteArray(),
        key.toByteArray(),
        toDeliver.toByteArray(),
        message.toByteArray()
    )
}

/**
 * Splits the prepared messages of a shard into the runs of [WRITE_SCRIPT], of at most 1,000
 * messages and 8 MiB of bytes each, with at least one message per run.
 *
 * @param messages The [prepared][prepare] messages.
 */
private fun chunks(messages: List<List<ByteArray>>): List<List<List<ByteArray>>> {
    val result = ArrayList<List<List<ByteArray>>>()
    var chunk = ArrayList<List<ByteArray>>()
    var bytes = 0L
    for (message in messages) {
        val size = message.last().size
        if (chunk.isNotEmpty() &&
            (chunk.size == MAX_CHUNK_MESSAGES || bytes + size > MAX_CHUNK_BYTES)) {
            result.add(chunk)
            chunk = ArrayList()
            bytes = 0
        }
        chunk.add(message)
        bytes += size
    }
    if (chunk.isNotEmpty()) {
        result.add(chunk)
    }
    return result
}
