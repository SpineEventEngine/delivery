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
import java.io.Serial

/**
 * An entry of the map of the inbox: the [tag][io.spine.delivery.storage.tag] of a shard, and
 * the messages of the shard, or `null` if the shard holds none.
 */
internal typealias ShardEntry = MutableMap.MutableEntry<String, HazelcastShard?>

/**
 * An operation on the entry of one shard, which Hazelcast runs on the member that owns
 * the entry.
 *
 * Hazelcast also runs a writing operation on the backup copy of the entry, kept on another
 * member. So a processor checks all of its input before it changes anything, and gives
 * the same result for the same entry, so that both copies stay the same.
 *
 * @param R The type of the result.
 *
 * @property classId The concrete processor class. Hazelcast writes its [ID][ClassId.id] with
 *   [FACTORY_ID] instead of the class name. The member that receives the processor passes
 *   the ID to [DeliverySerializableFactory] to create an instance, and then reads
 *   the processor's data into it. The ID must stay the same across versions, so that members
 *   of different versions understand each other during a rolling upgrade.
 */
internal abstract class ShardProcessor<R>(
    private val classId: ClassId
) : EntryProcessor<String, HazelcastShard?, R>, IdentifiedDataSerializable {

    /**
     * Returns [FACTORY_ID].
     */
    final override fun getFactoryId(): Int = FACTORY_ID

    /**
     * Returns the ID of [classId].
     */
    final override fun getClassId(): Int = classId.id

    companion object {

        /**
         * The version of the Java-serialized form, which Hazelcast does not use for this
         * class, as the class identifies itself for serialization.
         */
        @Serial
        private const val serialVersionUID: Long = 0L
    }
}

/**
 * An operation that only reads the entry of a shard.
 *
 * Such an operation does not run on the backup copy of the entry, as it changes nothing.
 *
 * @param R The type of the result.
 *
 * @param classId The concrete processor class.
 */
internal abstract class ReadingProcessor<R>(
    classId: ClassId
) : ShardProcessor<R>(classId), ReadOnly {

    /**
     * Returns `null`, so that the operation does not run on the backup copy of the entry.
     */
    final override fun getBackupProcessor(): EntryProcessor<String, HazelcastShard?, R>? = null

    companion object {

        /**
         * The version of the Java-serialized form, which Hazelcast does not use for this
         * class, as the class identifies itself for serialization.
         */
        @Serial
        private const val serialVersionUID: Long = 0L
    }
}

/**
 * Stores messages in their shard, replacing the stored messages with the same UUIDs.
 *
 * Always sets the value of the entry, even when the shard did not change, so that every write
 * is reported to the listeners of the map. The result is always `null`.
 */
internal class WriteMessages() : ShardProcessor<Unit?>(ClassId.WRITE) {

    /**
     * The index of the shard.
     */
    private var index = 0

    /**
     * The total number of shards.
     */
    private var ofTotal = 0

    /**
     * The messages to store.
     */
    private var messages: List<HeldMessage> = emptyList()

    /**
     * Creates the processor that stores the given messages in the given shard.
     */
    constructor(shard: ShardIndex, messages: List<HeldMessage>) : this() {
        this.index = shard.index
        this.ofTotal = shard.ofTotal
        this.messages = messages
    }

    /**
     * Stores the messages, creating the value of the shard if there is none.
     */
    override fun process(entry: ShardEntry): Unit? {
        val shard = entry.value ?: HazelcastShard(shardIndex(index, ofTotal))
        messages.forEach(shard.inbox::put)
        entry.setValue(shard)
        return null
    }

    /**
     * Writes the shard index and the messages, for sending the processor to the owner.
     */
    override fun writeData(out: ObjectDataOutput) {
        out.writeInt(index)
        out.writeInt(ofTotal)
        out.writeInt(messages.size)
        messages.forEach { it.writeTo(out) }
    }

    /**
     * Reads the shard index and the messages that [writeData] wrote.
     */
    override fun readData(input: ObjectDataInput) {
        index = input.readInt()
        ofTotal = input.readInt()
        messages = List(input.readInt()) { HeldMessage.readFrom(input) }
    }

    companion object {

        /**
         * The version of the Java-serialized form, which Hazelcast does not use for this
         * class, as the class identifies itself for serialization.
         */
        @Serial
        private const val serialVersionUID: Long = 0L
    }
}

/**
 * Removes the messages with the given UUIDs from their shard.
 *
 * Changes the entry only if a message is removed, so that a removal of absent messages is
 * not reported to the listeners of the map. Removes the entry when the shard becomes empty.
 *
 * The result tells whether any message was removed.
 */
internal class DeleteMessages() : ShardProcessor<Boolean>(ClassId.DELETE) {

    /**
     * The UUIDs of the messages to remove.
     */
    private var uuids: List<String> = emptyList()

    /**
     * Creates the processor that removes the messages with the given UUIDs.
     */
    constructor(uuids: List<String>) : this() {
        this.uuids = uuids
    }

    /**
     * Removes the messages, and tells whether any of them was removed.
     */
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

    /**
     * Writes the UUIDs, for sending the processor to the owner.
     */
    override fun writeData(out: ObjectDataOutput) {
        out.writeInt(uuids.size)
        uuids.forEach(out::writeString)
    }

    /**
     * Reads the UUIDs that [writeData] wrote.
     */
    override fun readData(input: ObjectDataInput) {
        uuids = List(input.readInt()) { checkNotNull(input.readString()) }
    }

    companion object {

        /**
         * The version of the Java-serialized form, which Hazelcast does not use for this
         * class, as the class identifies itself for serialization.
         */
        @Serial
        private const val serialVersionUID: Long = 0L
    }
}

/**
 * Finds the message with the given UUID in its shard.
 *
 * The result is the bytes of the message, or `null` if there is none.
 */
internal class FindMessage() : ReadingProcessor<ByteArray?>(ClassId.FIND) {

    /**
     * The UUID of the message to find.
     */
    private var uuid = ""

    /**
     * Creates the processor that finds the message with the given UUID.
     */
    constructor(uuid: String) : this() {
        this.uuid = uuid
    }

    /**
     * Returns the bytes of the message, or `null` if there is none.
     */
    override fun process(entry: ShardEntry): ByteArray? = entry.value?.inbox?.find(uuid)?.bytes

    /**
     * Writes the UUID, for sending the processor to the owner.
     */
    override fun writeData(out: ObjectDataOutput) {
        out.writeString(uuid)
    }

    /**
     * Reads the UUID that [writeData] wrote.
     */
    override fun readData(input: ObjectDataInput) {
        uuid = checkNotNull(input.readString())
    }

    companion object {

        /**
         * The version of the Java-serialized form, which Hazelcast does not use for this
         * class, as the class identifies itself for serialization.
         */
        @Serial
        private const val serialVersionUID: Long = 0L
    }
}

/**
 * Reads a page of the messages of a shard, as
 * [ShardInbox.page][io.spine.delivery.storage.ShardInbox.page] does.
 *
 * The result is the bytes of the messages, in the order of their order keys.
 */
internal class ReadPage() : ReadingProcessor<ArrayList<ByteArray>>(ClassId.PAGE) {

    /**
     * The time after which the page starts, or `null` to start from the start of the shard.
     */
    private var since: Timestamp? = null

    /**
     * The most messages to read.
     */
    private var limit = 0

    /**
     * Creates the processor that reads at most [limit] messages received after [since].
     */
    constructor(since: Timestamp?, limit: Int) : this() {
        this.since = since
        this.limit = limit
    }

    /**
     * Returns the bytes of the messages of the page.
     */
    override fun process(entry: ShardEntry): ArrayList<ByteArray> {
        val inbox = entry.value?.inbox ?: return ArrayList()
        return inbox.page(since, limit).mapTo(ArrayList()) { it.bytes }
    }

    /**
     * Writes the start time, if any, and the limit, for sending the processor to the owner.
     */
    override fun writeData(out: ObjectDataOutput) {
        val since = since
        out.writeBoolean(since != null)
        if (since != null) {
            out.writeLong(since.seconds)
            out.writeInt(since.nanos)
        }
        out.writeInt(limit)
    }

    /**
     * Reads the start time and the limit that [writeData] wrote.
     */
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

    companion object {

        /**
         * The version of the Java-serialized form, which Hazelcast does not use for this
         * class, as the class identifies itself for serialization.
         */
        @Serial
        private const val serialVersionUID: Long = 0L
    }
}

/**
 * Finds the newest message to deliver in a shard, as
 * [ShardInbox.newestToDeliver][io.spine.delivery.storage.ShardInbox.newestToDeliver] does.
 *
 * The result is the bytes of the message, or `null` if there is none.
 */
internal class FindNewestToDeliver : ReadingProcessor<ByteArray?>(ClassId.NEWEST) {

    /**
     * Returns the bytes of the message, or `null` if there is none.
     */
    override fun process(entry: ShardEntry): ByteArray? =
        entry.value?.inbox?.newestToDeliver()?.bytes

    /**
     * Writes nothing, as the processor has no data.
     */
    override fun writeData(out: ObjectDataOutput) = Unit

    /**
     * Reads nothing, as the processor has no data.
     */
    override fun readData(input: ObjectDataInput) = Unit

    companion object {

        /**
         * The version of the Java-serialized form, which Hazelcast does not use for this
         * class, as the class identifies itself for serialization.
         */
        @Serial
        private const val serialVersionUID: Long = 0L
    }
}

/**
 * Counts the messages of a shard.
 */
internal class CountMessages : ReadingProcessor<Int>(ClassId.COUNT) {

    /**
     * Returns the number of the messages, which is 0 if the shard has no entry.
     */
    override fun process(entry: ShardEntry): Int = entry.value?.inbox?.size ?: 0

    /**
     * Writes nothing, as the processor has no data.
     */
    override fun writeData(out: ObjectDataOutput) = Unit

    /**
     * Reads nothing, as the processor has no data.
     */
    override fun readData(input: ObjectDataInput) = Unit

    companion object {

        /**
         * The version of the Java-serialized form, which Hazelcast does not use for this
         * class, as the class identifies itself for serialization.
         */
        @Serial
        private const val serialVersionUID: Long = 0L
    }
}
