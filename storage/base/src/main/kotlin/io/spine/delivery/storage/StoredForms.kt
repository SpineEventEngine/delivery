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
import kotlin.uuid.Uuid

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
 * Returns the stored form of a shard session record written by the given write:
 * the bytes of a [StoredShardSession] that holds the write ID and the record.
 */
public fun sessionForm(writeId: Uuid, record: ShardSessionRecord): ByteArray =
    StoredShardSession.newBuilder()
        .setWriteId(writeId.toString())
        .setRecord(record)
        .build()
        .toByteArray()

/**
 * Parses the stored form of a shard session record, which [sessionForm] returns.
 *
 * @return The record and its write ID, with the given bytes as their stored form.
 * @throws IllegalStateException If the bytes are not such a form.
 */
public fun parseSession(form: ByteArray): Stored {
    val session = try {
        StoredShardSession.parseFrom(form)
    } catch (e: InvalidProtocolBufferException) {
        throw IllegalStateException("The stored bytes are not a `StoredShardSession`.", e)
    }
    val writeId = try {
        Uuid.parse(session.writeId)
    } catch (e: IllegalArgumentException) {
        throw IllegalStateException("The stored write ID `${session.writeId}` is not a UUID.", e)
    }
    return Stored(session.record, writeId, form)
}

/**
 * Checks that a page size is positive.
 *
 * @throws IllegalArgumentException If it is not.
 */
public fun checkPageSize(pageSize: Int) {
    require(pageSize > 0) { "The page size must be positive, but was $pageSize." }
}
