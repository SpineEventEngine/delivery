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

/**
 * Parses the stored bytes of an inbox message.
 *
 * @throws IllegalStateException if the bytes are not an `InboxMessage`
 */
public fun parseMessage(bytes: ByteArray): InboxMessage =
    try {
        InboxMessage.parseFrom(bytes)
    } catch (e: InvalidProtocolBufferException) {
        throw IllegalStateException("The stored bytes are not an `InboxMessage`.", e)
    }

/**
 * Parses the stored bytes of a shard session record.
 *
 * @throws IllegalStateException if the bytes are not a `ShardSessionRecord`
 */
public fun parseSession(bytes: ByteArray): ShardSessionRecord =
    try {
        ShardSessionRecord.parseFrom(bytes)
    } catch (e: InvalidProtocolBufferException) {
        throw IllegalStateException("The stored bytes are not a `ShardSessionRecord`.", e)
    }

/**
 * Checks that a page size is positive.
 *
 * @throws IllegalArgumentException if it is not
 */
public fun checkPageSize(pageSize: Int) {
    require(pageSize > 0) { "The page size must be positive, but was $pageSize." }
}
