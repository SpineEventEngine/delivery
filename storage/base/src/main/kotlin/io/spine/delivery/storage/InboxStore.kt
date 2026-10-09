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
import io.spine.server.delivery.InboxMessage
import io.spine.server.delivery.InboxMessageId
import io.spine.server.delivery.ShardIndex
import java.util.function.Consumer

/**
 * Stores inbox messages, grouped by the shard of each message.
 *
 * The shard of a message is the `index` of its `InboxMessageId`. Every operation on
 * messages costs in proportion to the shard it touches, or to the single message it names,
 * never to the number of all the stored messages.
 *
 * Within a shard, the messages are ordered by their order key, which compares, in turn:
 *  1. the time the message was received, `when_received`: first its seconds, then its nanos;
 *  2. the `version` of the message;
 *  3. the `uuid` of the message ID, by its Unicode code points.
 *
 * The numbers are compared as signed integers. Timestamps are not validated: a value outside
 * the range that `google.protobuf.Timestamp` allows is ordered by its numbers like any other.
 *
 * Several processes may share one store, for example several Delivery servers that use
 * the same database. Each of them then sees the changes made by the others.
 */
public interface InboxStore : AutoCloseable {

    /**
     * Stores the given messages, each in the shard of its own ID, replacing a stored message
     * with the same ID.
     *
     * The messages of each shard are written in one atomic step. A store that limits the size
     * of an atomic step may split the messages of one shard into a few such steps.
     *
     * Writing the same messages twice in a row leaves the same state as writing them once.
     */
    public fun write(messages: Iterable<InboxMessage>)

    /**
     * Removes the messages with the given IDs. An absent ID is not an error.
     *
     * The messages of each shard are removed in one atomic step, or in a few such steps,
     * as in [write].
     */
    public fun delete(ids: Iterable<InboxMessageId>)

    /**
     * Returns the message with the given ID, or `null` if there is none.
     */
    public fun find(id: InboxMessageId): InboxMessage?

    /**
     * Returns at most [pageSize] messages of the shard, in the ascending order of their
     * order keys: those whose `when_received` is strictly after [since], or from the start
     * of the shard, if [since] is `null`.
     *
     * @throws IllegalArgumentException If [pageSize] is not positive.
     */
    public fun page(shard: ShardIndex, since: Timestamp?, pageSize: Int): List<InboxMessage>

    /**
     * Returns the newest message to deliver in the shard, or `null` if there is none.
     *
     * That is the message in the `TO_DELIVER` status that was received last: the last one in
     * the order of the order keys, which compare the time of receiving first. Of the messages
     * received at the same time, it is the one with the highest version, and then with
     * the greatest UUID.
     */
    public fun newestToDeliver(shard: ShardIndex): InboxMessage?

    /**
     * Returns the number of messages in the shard.
     */
    public fun count(shard: ShardIndex): Int

    /**
     * Returns the number of messages in each of the given shards, including those
     * that hold none.
     */
    public fun count(shards: Collection<ShardIndex>): Map<ShardIndex, Int>

    /**
     * Returns the number of messages of every shard that holds at least one.
     */
    public fun counts(): Map<ShardIndex, Int>

    /**
     * Calls [onChange] with the shard of every change of the stored messages, after
     * the change is applied, whichever process sharing the store made it.
     *
     * Every write is reported. A delete that removes nothing is not.
     *
     * The listener is called on the thread that made the change, or on a thread of
     * the client of the database, so it must not block.
     */
    public fun subscribe(onChange: Consumer<ShardIndex>): Subscription

    /**
     * Calls [onMissed] whenever changes may have been made without being reported to
     * the listeners passed to [subscribe], for example after a connection to the backend
     * is established again.
     *
     * A store that reports every change, such as one in memory, never calls it.
     * The listener must not block.
     *
     * @return The subscription that stops the calls.
     */
    public fun subscribeToMissedChanges(onMissed: Runnable): Subscription = Subscription {}

    /**
     * Releases the subscriptions and the resources of the store.
     */
    override fun close()
}

/**
 * A subscription of a listener to the changes of a store.
 */
public fun interface Subscription {

    /**
     * Stops calling the listener.
     */
    public fun cancel()
}
