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

@file:JvmName("TestStoreData")

package io.spine.delivery.storage.given

import com.google.protobuf.ByteString
import com.google.protobuf.Timestamp
import io.spine.base.Identifier
import io.spine.core.Event
import io.spine.server.NodeId
import io.spine.server.delivery.InboxMessage
import io.spine.server.delivery.InboxMessageId
import io.spine.server.delivery.InboxMessageStatus
import io.spine.server.delivery.InboxMessageStatus.TO_DELIVER
import io.spine.server.delivery.ShardIndex
import io.spine.server.delivery.ShardSessionRecord
import io.spine.server.delivery.WorkerId
import com.google.protobuf.Any as AnyProto

/**
 * The number of shards of the test data.
 */
private const val SHARD_COUNT = 300

/**
 * Creates the index of a shard out of [SHARD_COUNT].
 *
 * Test data is built with `buildPartial()`, as the server receives it: messages parsed
 * off the wire are not validated.
 */
public fun shard(index: Int): ShardIndex =
    ShardIndex.newBuilder()
        .setIndex(index)
        .setOfTotal(SHARD_COUNT)
        .buildPartial()

/**
 * Creates a timestamp out of the given numbers, without checking their range.
 */
public fun time(seconds: Long, nanos: Int = 0): Timestamp =
    Timestamp.newBuilder()
        .setSeconds(seconds)
        .setNanos(nanos)
        .buildPartial()

/**
 * Creates an inbox message.
 *
 * @param payloadSize The number of bytes in the message of the wrapped event.
 */
public fun message(
    shard: ShardIndex,
    seconds: Long = 0,
    nanos: Int = 0,
    version: Int = 0,
    status: InboxMessageStatus = TO_DELIVER,
    uuid: String = Identifier.newUuid(),
    payloadSize: Int = 0
): InboxMessage {
    val payload = AnyProto.newBuilder()
        .setTypeUrl("type.spine.io/spine.test.Payload")
        .setValue(ByteString.copyFrom(ByteArray(payloadSize) { it.toByte() }))
    val event = Event.newBuilder()
        .setMessage(payload)
        .buildPartial()
    return InboxMessage.newBuilder()
        .setId(id(shard, uuid))
        .setEvent(event)
        .setStatus(status)
        .setWhenReceived(time(seconds, nanos))
        .setVersion(version)
        .buildPartial()
}

/**
 * Creates an inbox message ID.
 */
public fun id(shard: ShardIndex, uuid: String = Identifier.newUuid()): InboxMessageId =
    InboxMessageId.newBuilder()
        .setUuid(uuid)
        .setIndex(shard)
        .buildPartial()

/**
 * Returns a copy of the message with the given status.
 */
public fun InboxMessage.withStatus(status: InboxMessageStatus): InboxMessage =
    toBuilder().setStatus(status).buildPartial()

/**
 * Returns a copy of the message received at the given time, with the given version.
 */
public fun InboxMessage.receivedAt(seconds: Long, nanos: Int = 0, version: Int = this.version):
        InboxMessage =
    toBuilder()
        .setWhenReceived(time(seconds, nanos))
        .setVersion(version)
        .buildPartial()

/**
 * Creates a session record of the shard, picked by the given worker at the given time.
 */
public fun session(shard: ShardIndex, worker: String = "worker", pickedAt: Long = 0):
        ShardSessionRecord {
    val workerId = WorkerId.newBuilder()
        .setNodeId(NodeId.newBuilder().setValue("node"))
        .setValue(worker)
    return ShardSessionRecord.newBuilder()
        .setIndex(shard)
        .setWorker(workerId)
        .setWhenLastPicked(time(pickedAt))
        .buildPartial()
}
