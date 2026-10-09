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

@file:JvmName("RedisKeys")

package io.spine.delivery.storage.redis

/**
 * The number of digits of an encoded `int64`: 2⁶⁴ − 1 has 20.
 */
private const val LONG_DIGITS = 20

/**
 * The number of digits of an encoded `int32`: 2³² − 1 has 10.
 */
private const val INT_DIGITS = 10

/**
 * The number of characters of an [encoded order key][encodeOrderKey].
 */
internal const val ORDER_KEY_LENGTH = LONG_DIGITS + 1 + INT_DIGITS + 1 + INT_DIGITS

/**
 * The position at which the UUID starts in an element of the sorted set of a shard: after
 * the encoded order key and the `:` that follows it.
 *
 * Counted from 1, as Lua counts the characters of a string.
 */
internal const val UUID_POSITION = ORDER_KEY_LENGTH + 2

/**
 * Returns the key of the hash that holds the messages of a shard: from the UUID of each
 * message to its bytes.
 *
 * The part of the key in braces is the same for all the keys of a shard. A Redis cluster
 * therefore keeps them on one server, where a script can use them together.
 */
internal fun messagesKey(tag: String): String = "delivery:{inbox:$tag}:messages"

/**
 * Returns the key of the hash that holds the [encoded order key][encodeOrderKey] of each
 * message of a shard, by the UUID of the message.
 *
 * A write or a delete reads the old order key of a message here, to find the elements
 * of the message in the sorted sets of the shard.
 */
internal fun orderKeysKey(tag: String): String = "delivery:{inbox:$tag}:keys"

/**
 * Returns the key of the sorted set of all the messages of a shard.
 *
 * Each element is `<encoded order key>:<UUID>`. All the elements have the same score,
 * so Redis orders them by their bytes, which is the order of their order keys.
 */
internal fun allKey(tag: String): String = "delivery:{inbox:$tag}:all"

/**
 * Returns the key of the sorted set of the messages of a shard in the `TO_DELIVER` status.
 *
 * Its elements are those of the [sorted set of all the messages][allKey] that are to be
 * delivered.
 */
internal fun pendingKey(tag: String): String = "delivery:{inbox:$tag}:pending"

/**
 * The pattern that matches the [messages][messagesKey] hash of every shard.
 */
internal const val MESSAGES_PATTERN = "delivery:{inbox:*}:messages"

/**
 * Returns the [tag][io.spine.delivery.storage.tag] of the shard whose [messages][messagesKey] are
 * kept under the given key.
 */
internal fun tagOfMessagesKey(key: String): String =
    key.removePrefix("delivery:{inbox:").removeSuffix("}:messages")

/**
 * The key of the hash that holds the session records: from the [tag][io.spine.delivery.storage.tag]
 * of a shard to the bytes of its record.
 */
internal const val SESSIONS_KEY = "delivery:{sessions}"

/**
 * The channel on which the [tag][io.spine.delivery.storage.tag] of every shard whose messages
 * change is published.
 */
internal const val INBOX_CHANNEL = "delivery:changes:inbox"

/**
 * The channel on which the [tag][io.spine.delivery.storage.tag] of every shard whose session record
 * changes is published.
 */
internal const val SESSIONS_CHANNEL = "delivery:changes:sessions"

/**
 * Encodes the first three components of the order key, so that comparing two encoded keys
 * byte by byte orders them as the numbers they encode.
 *
 * Each number is offset so that it is never negative, then zero-padded: `seconds` plus 2⁶³
 * to 20 digits, `nanos` plus 2³¹ to 10 digits, and `version` plus 2³¹ to 10 digits. Flipping
 * the sign bit computes the offset value without overflow.
 */
internal fun encodeOrderKey(seconds: Long, nanos: Int, version: Int): String =
    "${encodeTime(seconds, nanos)}:${encodeInt(version)}"

/**
 * Encodes `when_received` as the first two components of an [encoded order key][encodeOrderKey].
 */
internal fun encodeTime(seconds: Long, nanos: Int): String =
    "${encodeLong(seconds)}:${encodeInt(nanos)}"

/**
 * Encodes a `long` as 20 digits, offset by 2⁶³ so that it is never negative.
 */
private fun encodeLong(value: Long): String =
    java.lang.Long.toUnsignedString(value xor Long.MIN_VALUE).padStart(LONG_DIGITS, '0')

/**
 * Encodes an `int` as 10 digits, offset by 2³¹ so that it is never negative.
 */
private fun encodeInt(value: Int): String =
    Integer.toUnsignedString(value xor Int.MIN_VALUE).padStart(INT_DIGITS, '0')
