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
 * Stores the inbox messages of the Delivery server, partitioned by shard.
 *
 * Every operation costs in proportion to the shard it touches, or to the single
 * message it names, never to the whole inbox.
 *
 * Within a shard, the messages are ordered by their order key: `when_received`
 * (seconds, then nanos), then `version`, then the UUID of the message ID. The numbers
 * are compared as signed integers, and the UUIDs by Unicode code points. Timestamps are
 * never validated: a value outside the range of `google.protobuf.Timestamp` is ordered
 * by its numbers like any other.
 */
public interface InboxStore : AutoCloseable {

    /**
     * Stores the given messages, each in the shard of its own ID, replacing a stored message
     * with the same ID.
     *
     * Each shard of the batch is written in one atomic step, or, where a backend limits
     * the size of a step, in a few atomic chunks. Writing the same messages twice in a row
     * leaves the same state.
     */
    public fun write(messages: Iterable<InboxMessage>)

    /**
     * Removes the messages with the given IDs. An absent ID is not an error.
     *
     * Each shard of the batch is changed in one atomic step, or in a few atomic chunks.
     */
    public fun delete(ids: Iterable<InboxMessageId>)

    /**
     * Returns the message with the given ID, or `null` if there is none.
     */
    public fun find(id: InboxMessageId): InboxMessage?

    /**
     * Returns at most [pageSize] messages of the shard, in the ascending order of their
     * order keys, whose `when_received` is strictly after [since], or all of them,
     * if [since] is `null`.
     *
     * @throws IllegalArgumentException if [pageSize] is not positive
     */
    public fun page(shard: ShardIndex, since: Timestamp?, pageSize: Int): List<InboxMessage>

    /**
     * Returns the message of the shard in the `TO_DELIVER` status with the largest order
     * key, or `null` if there is none.
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
     * Calls [onChange] with the shard of every change of the stored messages, made through
     * any node, after the change is applied.
     *
     * Every write is reported. A delete that removes nothing is not.
     * The listener must not block.
     */
    public fun subscribe(onChange: Consumer<ShardIndex>): Subscription

    /**
     * Calls [onMissed] whenever changes may have been made without being reported to
     * [subscribe]rs, for example after a connection to the backend is established again.
     *
     * A store that reports every change, such as one in memory, never calls it.
     * The listener must not block.
     */
    public fun subscribeToMissedChanges(onMissed: Runnable): Subscription = Subscription {}

    /**
     * Releases the subscriptions and the resources of the store.
     */
    override fun close()
}

/**
 * A subscription to the changes of a store.
 */
public fun interface Subscription {

    /**
     * Stops the delivery of the changes to the subscriber.
     */
    public fun cancel()
}
