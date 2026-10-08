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
 * The messages of one shard, indexed by UUID and ordered by the order key.
 *
 * Holds each message in a form [M] that a [MessageForm] describes: the `InboxMessage`
 * object in memory, or its bytes with the order-key fields in Hazelcast.
 *
 * The sorted maps hold the same objects as the hash map, with a comparator that reads
 * the order key from them, so there is no separate key object per message. The map of
 * the `TO_DELIVER` messages exists only while there are such messages.
 *
 * The number of messages is the size of the hash map, which holds each UUID once,
 * so overwrites and repeated removals cannot skew it.
 *
 * This class is not thread-safe. Its users guard every instance, for example by
 * synchronizing on it.
 *
 * @param M the form in which a message is held
 * @property shard the shard of the messages
 * @property form the description of the held form
 */
public class ShardInbox<M : Any>(
    public val shard: ShardIndex,
    private val form: MessageForm<M>
) {

    private val byUuid = HashMap<String, M>()
    private val all = TreeMap<Any, M>(form.comparator)
    private var toDeliver: TreeMap<Any, M>? = null

    /**
     * The number of messages in the shard.
     */
    public val size: Int
        get() = byUuid.size

    /**
     * The messages of the shard, in no particular order.
     *
     * A view that reflects later changes; it must be read under the same guard.
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
     * @return `true` if there was such a message
     */
    public fun remove(uuid: String): Boolean {
        val old = byUuid.remove(uuid) ?: return false
        unindex(old)
        return true
    }

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
     * Returns at most [limit] messages, in the ascending order of their order keys,
     * whose `when_received` is strictly after [since], or from the first one,
     * if [since] is `null`.
     *
     * @throws IllegalArgumentException if [limit] is not positive
     */
    public fun page(since: Timestamp?, limit: Int): List<M> {
        require(limit > 0) { "The page size must be positive, but was $limit." }
        val source = if (since == null) {
            all.values
        } else {
            all.tailMap(SinceProbe(since.seconds, since.nanos), false).values
        }
        // The size of the whole map is known at once; a sub-map counts its entries.
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
     * Returns the `TO_DELIVER` message with the largest order key, or `null` if there
     * is none.
     */
    public fun newestToDeliver(): M? = toDeliver?.lastEntry()?.value
}

/**
 * Describes the form in which a [ShardInbox] holds a message.
 *
 * @param M the held form
 */
public abstract class MessageForm<M : Any> {

    /**
     * Orders held messages by the order key, and places a [SinceProbe] after every
     * message received at exactly its time.
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

    @Suppress("UNCHECKED_CAST") // Only messages of this form and probes are compared.
    private fun compareAny(left: Any, right: Any): Int = when {
        // `TreeMap` checks the bound of a sub-map by comparing it with itself.
        left is SinceProbe && right is SinceProbe -> left.compareTo(right)
        left is SinceProbe -> -compareWithProbe(right as M, left)
        right is SinceProbe -> compareWithProbe(left as M, right)
        else -> compareMessages(left as M, right as M)
    }

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
 */
private class SinceProbe(val seconds: Long, val nanos: Int) : Comparable<SinceProbe> {

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
