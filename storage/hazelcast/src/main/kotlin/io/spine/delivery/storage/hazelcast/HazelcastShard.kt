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
 * The ID of the factory of the serializable classes of the Delivery stores.
 *
 * Fixed for the life of the stored data, as are the class IDs below.
 */
internal const val FACTORY_ID = 1_729

/**
 * The class IDs of the serializable classes of the Delivery stores.
 */
internal object ClassId {
    const val SHARD = 1
    const val WRITE = 2
    const val DELETE = 3
    const val FIND = 4
    const val PAGE = 5
    const val NEWEST = 6
    const val COUNT = 7
}

/**
 * Creates the serializable classes of the Delivery stores when Hazelcast reads them.
 */
internal class DeliverySerializableFactory : DataSerializableFactory {

    override fun create(typeId: Int): IdentifiedDataSerializable = when (typeId) {
        ClassId.SHARD -> HazelcastShard()
        ClassId.WRITE -> WriteMessages()
        ClassId.DELETE -> DeleteMessages()
        ClassId.FIND -> FindMessage()
        ClassId.PAGE -> ReadPage()
        ClassId.NEWEST -> FindNewestToDeliver()
        ClassId.COUNT -> CountMessages()
        else -> throw IllegalArgumentException("Unknown class ID: $typeId.")
    }
}

/**
 * An inbox message held by a member: its protobuf bytes, together with the fields of
 * its order key and its status, extracted by the caller.
 *
 * So the member never parses protobuf.
 */
internal class HeldMessage(
    val uuid: String,
    val seconds: Long,
    val nanos: Int,
    val version: Int,
    val toDeliver: Boolean,
    val bytes: ByteArray
) {

    fun writeTo(out: ObjectDataOutput) {
        out.writeString(uuid)
        out.writeLong(seconds)
        out.writeInt(nanos)
        out.writeInt(version)
        out.writeBoolean(toDeliver)
        out.writeByteArray(bytes)
    }

    companion object {

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
 * Describes a [HeldMessage] to a [ShardInbox].
 */
internal object HeldForm : MessageForm<HeldMessage>() {

    override fun uuid(message: HeldMessage): String = message.uuid

    override fun seconds(message: HeldMessage): Long = message.seconds

    override fun nanos(message: HeldMessage): Int = message.nanos

    override fun version(message: HeldMessage): Int = message.version

    override fun isToDeliver(message: HeldMessage): Boolean = message.toDeliver
}

/**
 * The messages of one shard, as the value of its entry in the inbox map.
 *
 * Serialized only when a partition migrates, replicas synchronize, or a split-brain
 * is merged: the inbox map keeps its values as objects, and processors change them
 * in place.
 */
internal class HazelcastShard() : IdentifiedDataSerializable {

    lateinit var inbox: ShardInbox<HeldMessage>
        private set

    constructor(shard: ShardIndex) : this() {
        inbox = ShardInbox(shard, HeldForm)
    }

    override fun getFactoryId(): Int = FACTORY_ID

    override fun getClassId(): Int = ClassId.SHARD

    override fun writeData(out: ObjectDataOutput) {
        out.writeInt(inbox.shard.index)
        out.writeInt(inbox.shard.ofTotal)
        val messages = inbox.messages
        out.writeInt(messages.size)
        messages.forEach { it.writeTo(out) }
    }

    override fun readData(input: ObjectDataInput) {
        inbox = ShardInbox(shardIndex(input.readInt(), input.readInt()), HeldForm)
        repeat(input.readInt()) {
            inbox.put(HeldMessage.readFrom(input))
        }
    }
}

/**
 * Creates a shard index, without validating it, as the server stores what clients send.
 */
internal fun shardIndex(index: Int, ofTotal: Int): ShardIndex =
    ShardIndex.newBuilder()
        .setIndex(index)
        .setOfTotal(ofTotal)
        .buildPartial()
