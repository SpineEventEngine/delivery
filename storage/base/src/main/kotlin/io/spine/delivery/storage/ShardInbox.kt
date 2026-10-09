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

package io.spine.delivery.storage

import com.google.protobuf.Timestamp
import io.spine.server.delivery.ShardIndex
import java.util.TreeMap
import kotlin.math.min

/**
 * The inbox messages of one shard, kept in the order in which they are read.
 *
 * Each message is identified by the UUID of its ID: the `uuid` field of its
 * `InboxMessageId`. A shard stores at most one message per UUID, so storing a message
 * replaces the stored one with the same UUID.
 *
 * The messages are ordered by their order key, which compares, in turn:
 *  1. the time the message was received, `when_received`: first its seconds, then its nanos;
 *  2. the version of the message;
 *  3. the UUID, by its Unicode code points.
 *
 * Pages of messages are read in this order. As the time of receiving comes first, the order
 * is chronological, and the version and the UUID only break ties.
 *
 * The number of messages is the number of distinct UUIDs, so storing a message again, or
 * removing an absent one, never skews it.
 *
 * This class is not thread-safe. To use an instance from several threads, synchronize on it.
 *
 * @param M The type of the stored messages.
 *
 * @property shard The shard of the messages.
 * @property form Reads the order key and the status of a stored message.
 */
public class ShardInbox<M : Any>(
    public val shard: ShardIndex,
    private val form: MessageForm<M>
) {

    /**
     * The messages by the UUIDs of their IDs.
     */
    private val byUuid = HashMap<String, M>()

    /**
     * All the messages, in the order of their order keys.
     *
     * Each message is both a key and its value, and [form] reads the order key from it,
     * so the map creates no key object per message.
     */
    private val all = TreeMap<Any, M>(form.comparator)

    /**
     * The messages in the `TO_DELIVER` status, in the order of their order keys.
     *
     * `null` while there are none, so that a shard without such messages holds no empty map.
     */
    private var toDeliver: TreeMap<Any, M>? = null

    /**
     * The number of messages in the shard.
     */
    public val size: Int
        get() = byUuid.size

    /**
     * The messages of the shard, in no particular order.
     *
     * The collection reflects later changes of the shard, so read it while holding the same
     * lock as for the other operations.
     */
    public val messages: Collection<M>
        get() = byUuid.values

    /**
     * Stores the message, replacing a stored one with the same UUID.
     */
    public fun put(message: M) {
        val old = byUuid.put(form.uuid(message), message)
        if (old != null) {
            unindex(old)
        }
        all[message] = message
        if (form.isToDeliver(message)) {
            val pending = toDeliver ?: TreeMap<Any, M>(form.comparator).also { toDeliver = it }
            pending[message] = message
        }
    }

    /**
     * Removes the message with the given UUID.
     *
     * @return `true` if there was such a message.
     */
    public fun remove(uuid: String): Boolean {
        val old = byUuid.remove(uuid) ?: return false
        unindex(old)
        return true
    }

    /**
     * Removes the message from the ordered maps.
     */
    private fun unindex(message: M) {
        all.remove(message)
        val pending = toDeliver ?: return
        pending.remove(message)
        if (pending.isEmpty()) {
            toDeliver = null
        }
    }

    /**
     * Returns the message with the given UUID, or `null` if there is none.
     */
    public fun find(uuid: String): M? = byUuid[uuid]

    /**
     * Returns at most [limit] messages, in the order of their order keys: those received
     * strictly after [since], or from the start of the shard, if [since] is `null`.
     *
     * @throws IllegalArgumentException If [limit] is not positive.
     */
    public fun page(since: Timestamp?, limit: Int): List<M> {
        checkPageSize(limit)
        val source = if (since == null) {
            all.values
        } else {
            all.tailMap(SinceProbe(since.seconds, since.nanos), false).values
        }
        // The size of the whole map is known at once, while a sub-map counts its entries.
        val result = ArrayList<M>(min(limit, all.size))
        for (message in source) {
            if (result.size == limit) {
                break
            }
            result.add(message)
        }
        return result
    }

    /**
     * Returns the newest message to deliver, or `null` if there is none.
     *
     * That is the message in the `TO_DELIVER` status that was received last: the last one in
     * the order of the order keys, which compare the time of receiving first. Of the messages
     * received at the same time, it is the one with the highest version, and then with
     * the greatest UUID, so that every store returns the same message.
     */
    public fun newestToDeliver(): M? = toDeliver?.lastEntry()?.value
}

/**
 * Reads the parts of a stored message that a [ShardInbox] needs: the UUID of its ID,
 * the components of its order key, and whether it is to be delivered.
 *
 * @param M The type of the stored messages.
 */
public abstract class MessageForm<M : Any> {

    /**
     * Orders the stored messages by their order keys.
     *
     * Also compares a message with a [SinceProbe], which sorts after every message received
     * at exactly its time, so that a page can start right after a given time.
     */
    internal val comparator: Comparator<Any> = Comparator { left, right -> compareAny(left, right) }

    /**
     * Returns the UUID of the message ID.
     */
    public abstract fun uuid(message: M): String

    /**
     * Returns the seconds of `when_received`.
     */
    public abstract fun seconds(message: M): Long

    /**
     * Returns the nanos of `when_received`.
     */
    public abstract fun nanos(message: M): Int

    /**
     * Returns the version of the message.
     */
    public abstract fun version(message: M): Int

    /**
     * Tells whether the message is in the `TO_DELIVER` status.
     */
    public abstract fun isToDeliver(message: M): Boolean

    /**
     * Compares two keys of the ordered maps of a [ShardInbox]: stored messages, or
     * a [SinceProbe].
     */
    @Suppress("UNCHECKED_CAST") // Only messages of this form and probes are compared.
    private fun compareAny(left: Any, right: Any): Int = when {
        // `TreeMap` checks the bound of a sub-map by comparing it with itself.
        left is SinceProbe && right is SinceProbe -> left.compareTo(right)
        left is SinceProbe -> -compareWithProbe(right as M, left)
        right is SinceProbe -> compareWithProbe(left as M, right)
        else -> compareMessages(left as M, right as M)
    }

    /**
     * Compares two messages by their order keys.
     */
    private fun compareMessages(left: M, right: M): Int {
        var result = seconds(left).compareTo(seconds(right))
        if (result != 0) {
            return result
        }
        result = nanos(left).compareTo(nanos(right))
        if (result != 0) {
            return result
        }
        result = version(left).compareTo(version(right))
        if (result != 0) {
            return result
        }
        return compareByCodePoints(uuid(left), uuid(right))
    }

    /**
     * Compares the message with the probe, never returning 0: a message received at
     * exactly the time of the probe sorts before it.
     */
    private fun compareWithProbe(message: M, probe: SinceProbe): Int {
        val result = seconds(message).compareTo(probe.seconds)
        if (result != 0) {
            return result
        }
        val byNanos = nanos(message).compareTo(probe.nanos)
        return if (byNanos != 0) byNanos else -1
    }
}

/**
 * A lookup key that sorts after every message received at exactly the given time,
 * and before every message received later.
 *
 * A page that starts at the probe therefore starts right after the given time.
 *
 * @property seconds The seconds of the time.
 * @property nanos The nanos of the time.
 */
private class SinceProbe(val seconds: Long, val nanos: Int) : Comparable<SinceProbe> {

    /**
     * Compares the probe with another one by their times.
     */
    override fun compareTo(other: SinceProbe): Int {
        val result = seconds.compareTo(other.seconds)
        return if (result != 0) result else nanos.compareTo(other.nanos)
    }
}

/**
 * Compares two strings by their Unicode code points, which orders them as their UTF-8
 * bytes compare.
 *
 * `String.compareTo` compares UTF-16 code units instead, which orders characters outside
 * the Basic Multilingual Plane differently.
 */
internal fun compareByCodePoints(left: String, right: String): Int {
    var i = 0
    var j = 0
    while (i < left.length && j < right.length) {
        val leftPoint = left.codePointAt(i)
        val rightPoint = right.codePointAt(j)
        if (leftPoint != rightPoint) {
            return leftPoint.compareTo(rightPoint)
        }
        i += Character.charCount(leftPoint)
        j += Character.charCount(rightPoint)
    }
    return (left.length - i).compareTo(right.length - j)
}
