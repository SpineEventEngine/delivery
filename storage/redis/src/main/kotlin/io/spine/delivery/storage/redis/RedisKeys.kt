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
 * The length of an [encoded order key][encodeOrderKey].
 */
internal const val ORDER_KEY_LENGTH = LONG_DIGITS + 1 + INT_DIGITS + 1 + INT_DIGITS

/**
 * The hash of a shard's messages, from the UUID to the message bytes.
 */
internal fun messagesKey(tag: String): String = "delivery:{inbox:$tag}:messages"

/**
 * The hash of a shard's encoded order keys, from the UUID to the key.
 */
internal fun orderKeysKey(tag: String): String = "delivery:{inbox:$tag}:keys"

/**
 * The sorted set of all of a shard's messages, as `<encoded order key>:<UUID>`.
 */
internal fun allKey(tag: String): String = "delivery:{inbox:$tag}:all"

/**
 * The sorted set of a shard's `TO_DELIVER` messages, as `<encoded order key>:<UUID>`.
 */
internal fun pendingKey(tag: String): String = "delivery:{inbox:$tag}:pending"

/**
 * The pattern that matches the [messages][messagesKey] hash of every shard.
 */
internal const val MESSAGES_PATTERN = "delivery:{inbox:*}:messages"

/**
 * Returns the shard tag of a [messages][messagesKey] hash.
 */
internal fun tagOfMessagesKey(key: String): String =
    key.removePrefix("delivery:{inbox:").removeSuffix("}:messages")

/**
 * The hash of the shard session records, from the shard tag to the record bytes.
 */
internal const val SESSIONS_KEY = "delivery:{sessions}"

/**
 * The channel on which the tag of every changed shard of the inbox is published.
 */
internal const val INBOX_CHANNEL = "delivery:changes:inbox"

/**
 * The channel on which the tag of the shard of every changed session record is published.
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

private fun encodeLong(value: Long): String =
    java.lang.Long.toUnsignedString(value xor Long.MIN_VALUE).padStart(LONG_DIGITS, '0')

private fun encodeInt(value: Int): String =
    Integer.toUnsignedString(value xor Int.MIN_VALUE).padStart(INT_DIGITS, '0')
