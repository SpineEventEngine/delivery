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

import com.google.protobuf.Timestamp
import com.hazelcast.core.ReadOnly
import com.hazelcast.map.EntryProcessor
import com.hazelcast.nio.ObjectDataInput
import com.hazelcast.nio.ObjectDataOutput
import com.hazelcast.nio.serialization.IdentifiedDataSerializable
import io.spine.server.delivery.ShardIndex

/**
 * An entry of the inbox map: a shard tag, and the shard's messages, if there are any.
 */
internal typealias ShardEntry = MutableMap.MutableEntry<String, HazelcastShard?>

/**
 * A processor of a shard entry, identified for serialization.
 *
 * Processors check all of their input before they change anything, and are deterministic,
 * so that the backup replica, which runs the same processor, ends in the same state.
 *
 * @param R The type of the result.
 */
internal abstract class ShardProcessor<R>(
    private val classId: Int
) : EntryProcessor<String, HazelcastShard?, R>, IdentifiedDataSerializable {

    final override fun getFactoryId(): Int = FACTORY_ID

    final override fun getClassId(): Int = classId
}

/**
 * A processor that only reads its shard, and therefore needs no backup processor.
 */
internal abstract class ReadingProcessor<R>(classId: Int) : ShardProcessor<R>(classId), ReadOnly {

    final override fun getBackupProcessor(): EntryProcessor<String, HazelcastShard?, R>? = null
}

/**
 * Stores the messages in their shard, replacing stored messages with the same UUIDs.
 *
 * Always ends with `setValue`, so that every write raises an event.
 */
internal class WriteMessages() : ShardProcessor<Unit?>(ClassId.WRITE) {

    private var index = 0
    private var ofTotal = 0
    private var messages: List<HeldMessage> = emptyList()

    constructor(shard: ShardIndex, messages: List<HeldMessage>) : this() {
        this.index = shard.index
        this.ofTotal = shard.ofTotal
        this.messages = messages
    }

    override fun process(entry: ShardEntry): Unit? {
        val shard = entry.value ?: HazelcastShard(shardIndex(index, ofTotal))
        messages.forEach(shard.inbox::put)
        entry.setValue(shard)
        return null
    }

    override fun writeData(out: ObjectDataOutput) {
        out.writeInt(index)
        out.writeInt(ofTotal)
        out.writeInt(messages.size)
        messages.forEach { it.writeTo(out) }
    }

    override fun readData(input: ObjectDataInput) {
        index = input.readInt()
        ofTotal = input.readInt()
        messages = List(input.readInt()) { HeldMessage.readFrom(input) }
    }
}

/**
 * Removes the messages with the given UUIDs from the shard.
 *
 * Changes the entry only if a message is removed, and removes the entry when the shard
 * becomes empty. The result tells whether any message was removed.
 */
internal class DeleteMessages() : ShardProcessor<Boolean>(ClassId.DELETE) {

    private var uuids: List<String> = emptyList()

    constructor(uuids: List<String>) : this() {
        this.uuids = uuids
    }

    override fun process(entry: ShardEntry): Boolean {
        val shard = entry.value ?: return false
        var removed = false
        for (uuid in uuids) {
            removed = shard.inbox.remove(uuid) || removed
        }
        if (removed) {
            entry.setValue(if (shard.inbox.size == 0) null else shard)
        }
        return removed
    }

    override fun writeData(out: ObjectDataOutput) {
        out.writeInt(uuids.size)
        uuids.forEach(out::writeString)
    }

    override fun readData(input: ObjectDataInput) {
        uuids = List(input.readInt()) { checkNotNull(input.readString()) }
    }
}

/**
 * Returns the bytes of the message with the given UUID, or `null` if there is none.
 */
internal class FindMessage() : ReadingProcessor<ByteArray?>(ClassId.FIND) {

    private var uuid = ""

    constructor(uuid: String) : this() {
        this.uuid = uuid
    }

    override fun process(entry: ShardEntry): ByteArray? = entry.value?.inbox?.find(uuid)?.bytes

    override fun writeData(out: ObjectDataOutput) {
        out.writeString(uuid)
    }

    override fun readData(input: ObjectDataInput) {
        uuid = checkNotNull(input.readString())
    }
}

/**
 * Returns the bytes of a page of the shard's messages.
 */
internal class ReadPage() : ReadingProcessor<ArrayList<ByteArray>>(ClassId.PAGE) {

    private var since: Timestamp? = null
    private var limit = 0

    constructor(since: Timestamp?, limit: Int) : this() {
        this.since = since
        this.limit = limit
    }

    override fun process(entry: ShardEntry): ArrayList<ByteArray> {
        val inbox = entry.value?.inbox ?: return ArrayList()
        return inbox.page(since, limit).mapTo(ArrayList()) { it.bytes }
    }

    override fun writeData(out: ObjectDataOutput) {
        val since = since
        out.writeBoolean(since != null)
        if (since != null) {
            out.writeLong(since.seconds)
            out.writeInt(since.nanos)
        }
        out.writeInt(limit)
    }

    override fun readData(input: ObjectDataInput) {
        since = if (input.readBoolean()) {
            Timestamp.newBuilder()
                .setSeconds(input.readLong())
                .setNanos(input.readInt())
                .buildPartial()
        } else {
            null
        }
        limit = input.readInt()
    }
}

/**
 * Returns the bytes of the shard's `TO_DELIVER` message with the largest order key,
 * or `null` if there is none.
 */
internal class FindNewestToDeliver : ReadingProcessor<ByteArray?>(ClassId.NEWEST) {

    override fun process(entry: ShardEntry): ByteArray? =
        entry.value?.inbox?.newestToDeliver()?.bytes

    override fun writeData(out: ObjectDataOutput) = Unit

    override fun readData(input: ObjectDataInput) = Unit
}

/**
 * Returns the number of the shard's messages.
 */
internal class CountMessages : ReadingProcessor<Int>(ClassId.COUNT) {

    override fun process(entry: ShardEntry): Int = entry.value?.inbox?.size ?: 0

    override fun writeData(out: ObjectDataOutput) = Unit

    override fun readData(input: ObjectDataInput) = Unit
}
