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

import com.hazelcast.nio.ObjectDataInput
import com.hazelcast.nio.ObjectDataOutput
import com.hazelcast.nio.serialization.DataSerializableFactory
import com.hazelcast.nio.serialization.IdentifiedDataSerializable
import io.spine.delivery.storage.MessageForm
import io.spine.delivery.storage.ShardInbox
import io.spine.server.delivery.InboxMessage
import io.spine.server.delivery.InboxMessageStatus.TO_DELIVER
import io.spine.server.delivery.ShardIndex

/**
 * The ID of the factory of the classes that the Delivery stores send between the members of
 * a Hazelcast cluster.
 *
 * Hazelcast identifies such a class by this factory ID together with the [class ID][ClassId.id],
 * instead of the class name. Both IDs must stay the same across versions, so that members
 * of different versions understand each other.
 */
internal const val FACTORY_ID = 1_729

/**
 * The classes that the Delivery stores send between the members of a Hazelcast cluster,
 * each with its class ID.
 *
 * The IDs are explicit, never the ordinals, so that reordering or adding entries cannot
 * change them.
 *
 * @property id The class ID, which Hazelcast writes with [FACTORY_ID] instead of
 *   the class name. It must stay the same across versions.
 */
internal enum class ClassId(val id: Int) {

    /**
     * The messages of a shard, [HazelcastShard].
     */
    SHARD(1),

    /**
     * The processor that writes messages, [WriteMessages].
     */
    WRITE(2),

    /**
     * The processor that removes messages, [DeleteMessages].
     */
    DELETE(3),

    /**
     * The processor that finds a message, [FindMessage].
     */
    FIND(4),

    /**
     * The processor that reads a page of messages, [ReadPage].
     */
    PAGE(5),

    /**
     * The processor that finds the newest message to deliver, [FindNewestToDeliver].
     */
    NEWEST(6),

    /**
     * The processor that counts messages, [CountMessages].
     */
    COUNT(7);

    companion object {

        /**
         * Returns the class with the given ID, or `null` if there is none.
         */
        fun of(id: Int): ClassId? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Creates the instances of the classes of the Delivery stores, into which Hazelcast reads
 * the data that another member sent.
 *
 * Matches all the [ClassId]s, so that the compiler requires a creator for each of them.
 */
internal class DeliverySerializableFactory : DataSerializableFactory {

    /**
     * Creates an empty instance of the class with the given ID.
     *
     * @throws IllegalArgumentException If no class has the ID.
     */
    override fun create(typeId: Int): IdentifiedDataSerializable = when (ClassId.of(typeId)) {
        ClassId.SHARD -> HazelcastShard()
        ClassId.WRITE -> WriteMessages()
        ClassId.DELETE -> DeleteMessages()
        ClassId.FIND -> FindMessage()
        ClassId.PAGE -> ReadPage()
        ClassId.NEWEST -> FindNewestToDeliver()
        ClassId.COUNT -> CountMessages()
        null -> throw IllegalArgumentException("Unknown class ID: $typeId.")
    }
}

/**
 * An inbox message as the entry of its shard holds it: the bytes of the message, with
 * the parts of the message that the shard needs to order it.
 *
 * The parts are read from the message before it is sent to the member that owns the shard,
 * so that member never parses Protobuf.
 *
 * @property uuid The `uuid` of the message ID.
 * @property seconds The seconds of the time the message was received.
 * @property nanos The nanos of the time the message was received.
 * @property version The version of the message.
 * @property toDeliver Whether the message is in the `TO_DELIVER` status.
 * @property bytes The serialized message.
 */
internal class HeldMessage(
    val uuid: String,
    val seconds: Long,
    val nanos: Int,
    val version: Int,
    val toDeliver: Boolean,
    val bytes: ByteArray
) {

    /**
     * Writes the message to the given output, for sending it to another member.
     */
    fun writeTo(out: ObjectDataOutput) {
        out.writeString(uuid)
        out.writeLong(seconds)
        out.writeInt(nanos)
        out.writeInt(version)
        out.writeBoolean(toDeliver)
        out.writeByteArray(bytes)
    }

    companion object {

        /**
         * Returns the given inbox message as a shard entry holds it.
         */
        fun of(message: InboxMessage): HeldMessage {
            val received = message.whenReceived
            return HeldMessage(
                uuid = message.id.uuid,
                seconds = received.seconds,
                nanos = received.nanos,
                version = message.version,
                toDeliver = message.status == TO_DELIVER,
                bytes = message.toByteArray()
            )
        }

        /**
         * Reads a message that [writeTo] wrote.
         */
        fun readFrom(input: ObjectDataInput): HeldMessage =
            HeldMessage(
                uuid = checkNotNull(input.readString()),
                seconds = input.readLong(),
                nanos = input.readInt(),
                version = input.readInt(),
                toDeliver = input.readBoolean(),
                bytes = checkNotNull(input.readByteArray())
            )
    }
}

/**
 * Reads the order key and the status of a [HeldMessage] for a [ShardInbox].
 */
internal object HeldForm : MessageForm<HeldMessage>() {

    override fun uuid(message: HeldMessage): String = message.uuid

    override fun seconds(message: HeldMessage): Long = message.seconds

    override fun nanos(message: HeldMessage): Int = message.nanos

    override fun version(message: HeldMessage): Int = message.version

    override fun isToDeliver(message: HeldMessage): Boolean = message.toDeliver
}

/**
 * The messages of one shard, as the value of its entry in the map of the inbox.
 *
 * Normally, the map keeps the value as an object on the member that owns the entry, and
 * the entry processors change it in place. Hazelcast serializes the value only to copy it
 * to another member: when the members of the cluster change, when it updates the backup copy
 * of the entry, and when two parts of a cluster join again after a network split.
 */
internal class HazelcastShard() : IdentifiedDataSerializable {

    /**
     * The messages of the shard.
     */
    lateinit var inbox: ShardInbox<HeldMessage>
        private set

    /**
     * Creates the value of a shard that holds no messages yet.
     */
    constructor(shard: ShardIndex) : this() {
        inbox = ShardInbox(shard, HeldForm)
    }

    /**
     * Returns [FACTORY_ID].
     */
    override fun getFactoryId(): Int = FACTORY_ID

    /**
     * Returns the ID of [ClassId.SHARD].
     */
    override fun getClassId(): Int = ClassId.SHARD.id

    /**
     * Writes the shard index and all the messages.
     */
    override fun writeData(out: ObjectDataOutput) {
        out.writeInt(inbox.shard.index)
        out.writeInt(inbox.shard.ofTotal)
        val messages = inbox.messages
        out.writeInt(messages.size)
        messages.forEach { it.writeTo(out) }
    }

    /**
     * Reads the shard index and all the messages that [writeData] wrote.
     */
    override fun readData(input: ObjectDataInput) {
        inbox = ShardInbox(shardIndex(input.readInt(), input.readInt()), HeldForm)
        repeat(input.readInt()) {
            inbox.put(HeldMessage.readFrom(input))
        }
    }
}

/**
 * Creates a shard index, without validating it, so that any index that was stored can be
 * read back.
 */
internal fun shardIndex(index: Int, ofTotal: Int): ShardIndex =
    ShardIndex.newBuilder()
        .setIndex(index)
        .setOfTotal(ofTotal)
        .buildPartial()
