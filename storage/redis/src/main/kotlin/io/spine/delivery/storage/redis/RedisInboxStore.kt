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
 * The most messages that one script call writes or deletes.
 */
private const val MAX_CHUNK_MESSAGES = 1_000

/**
 * The most message bytes that one script call writes, unless a single message is larger.
 */
private const val MAX_CHUNK_BYTES = 8 * 1024 * 1024

/**
 * The number of Redis keys scanned per `SCAN` call.
 */
private const val SCAN_COUNT = 1_000

/**
 * An [InboxStore] kept in Redis, in four keys per shard.
 *
 * Writes and deletes of each shard run as Lua scripts, in chunks of at most 1,000
 * messages and 8 MiB of message bytes, each of which is atomic. Pages and the newest
 * message to deliver are read by scripts too, each in one round trip. Every argument is
 * prepared before the first script call, because a script that fails midway keeps its
 * earlier writes.
 */
public class RedisInboxStore internal constructor(
    private val client: RedissonClient
) : InboxStore, WithLogging {

    private val listeners = ChangeListeners()
    private val channel = ChangeChannel(client, INBOX_CHANNEL) { listeners.changed(shardOf(it)) }
    private val writeScript = LuaScript(client, WRITE_SCRIPT)
    private val deleteScript = LuaScript(client, DELETE_SCRIPT)
    private val pageScript = LuaScript(client, PAGE_SCRIPT)
    private val newestScript = LuaScript(client, NEWEST_SCRIPT)

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

    override fun delete(ids: Iterable<InboxMessageId>) {
        val prepared = ids.groupBy { it.index.tag() }
            .mapValues { (_, batch) -> batch.map { it.uuid.toByteArray() } }
        for ((tag, uuids) in prepared) {
            for (chunk in uuids.chunked(MAX_CHUNK_MESSAGES)) {
                deleteScript.run(READ_WRITE, shardKeys(tag), listOf(tag.toByteArray()) + chunk)
            }
        }
    }

    override fun find(id: InboxMessageId): InboxMessage? =
        client.getMap<String, ByteArray>(messagesKey(id.index.tag()), HASH_CODEC)[id.uuid]
            ?.let(::parseMessage)

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

    override fun newestToDeliver(shard: ShardIndex): InboxMessage? {
        val tag = shard.tag()
        val result = newestScript.run(
            READ_ONLY, listOf(messagesKey(tag), pendingKey(tag)), emptyList()
        )
        reportMissing(tag, result)
        return (result.getOrNull(1) as ByteArray?)?.let(::parseMessage)
    }

    override fun count(shard: ShardIndex): Int =
        client.getMap<String, ByteArray>(messagesKey(shard.tag()), HASH_CODEC).size

    override fun count(shards: Collection<ShardIndex>): Map<ShardIndex, Int> =
        countAll(shards.associateBy { messagesKey(it.tag()) })

    override fun counts(): Map<ShardIndex, Int> {
        val scan = KeysScanOptions.defaults()
            .pattern(MESSAGES_PATTERN)
            .chunkSize(SCAN_COUNT)
        val shardsByKey = client.keys.getKeys(scan)
            .associateWith { shardOf(tagOfMessagesKey(it)) }
        return countAll(shardsByKey).filterValues { it > 0 }
    }

    /**
     * Returns the sizes of the given messages hashes, read in one pipeline.
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

    override fun subscribe(onChange: Consumer<ShardIndex>): Subscription = listeners.add(onChange)

    override fun subscribeToMissedChanges(onMissed: Runnable): Subscription =
        channel.subscribeToMissed(onMissed)

    override fun close() {
        try {
            channel.close()
        } finally {
            listeners.clear()
        }
    }

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
 * The keys of a shard, in the order the write and delete scripts expect them.
 */
private fun shardKeys(tag: String): List<String> =
    listOf(messagesKey(tag), orderKeysKey(tag), allKey(tag), pendingKey(tag))

/**
 * Returns the script arguments of a message: its UUID, encoded order key, `TO_DELIVER` flag,
 * and bytes.
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
 * Cuts the prepared messages into chunks of at most `MAX_CHUNK_MESSAGES` messages and
 * `MAX_CHUNK_BYTES` of message bytes, with at least one message per chunk.
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
