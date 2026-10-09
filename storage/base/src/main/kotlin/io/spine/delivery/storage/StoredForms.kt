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

@file:JvmName("StoredForms")

package io.spine.delivery.storage

import com.google.protobuf.InvalidProtocolBufferException
import io.spine.server.delivery.InboxMessage
import io.spine.server.delivery.ShardSessionRecord
import java.nio.ByteBuffer
import java.util.UUID

/**
 * Parses the stored bytes of an inbox message.
 *
 * @throws IllegalStateException If the bytes are not an `InboxMessage`.
 */
public fun parseMessage(bytes: ByteArray): InboxMessage =
    try {
        InboxMessage.parseFrom(bytes)
    } catch (e: InvalidProtocolBufferException) {
        throw IllegalStateException("The stored bytes are not an `InboxMessage`.", e)
    }

/**
 * The number of bytes of the write ID that starts the stored form of a session record.
 */
private const val WRITE_ID_SIZE = 2 * Long.SIZE_BYTES

/**
 * Returns the stored form of a shard session record written by the given write:
 * the 16 bytes of the write ID, followed by the bytes of the record.
 */
public fun sessionForm(writeId: UUID, record: ShardSessionRecord): ByteArray {
    val bytes = record.toByteArray()
    return ByteBuffer.allocate(WRITE_ID_SIZE + bytes.size)
        .putLong(writeId.mostSignificantBits)
        .putLong(writeId.leastSignificantBits)
        .put(bytes)
        .array()
}

/**
 * Parses the stored form of a shard session record, which [sessionForm] returns.
 *
 * @return The record and its write ID, with the given bytes as their stored form.
 * @throws IllegalStateException If the bytes are not such a form.
 */
public fun parseSession(form: ByteArray): Stored {
    check(form.size >= WRITE_ID_SIZE) {
        "The stored bytes are too short to start with a write ID."
    }
    val buffer = ByteBuffer.wrap(form)
    val writeId = UUID(buffer.getLong(0), buffer.getLong(Long.SIZE_BYTES))
    val record = try {
        ShardSessionRecord.parser().parseFrom(form, WRITE_ID_SIZE, form.size - WRITE_ID_SIZE)
    } catch (e: InvalidProtocolBufferException) {
        throw IllegalStateException("The stored bytes do not end with a `ShardSessionRecord`.", e)
    }
    return Stored(record, writeId, form)
}

/**
 * Checks that a page size is positive.
 *
 * @throws IllegalArgumentException If it is not.
 */
public fun checkPageSize(pageSize: Int) {
    require(pageSize > 0) { "The page size must be positive, but was $pageSize." }
}
