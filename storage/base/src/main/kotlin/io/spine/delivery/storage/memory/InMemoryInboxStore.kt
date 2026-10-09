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

package io.spine.delivery.storage.memory

import com.google.protobuf.Timestamp
import io.spine.delivery.storage.ChangeListeners
import io.spine.delivery.storage.InboxStore
import io.spine.delivery.storage.MessageForm
import io.spine.delivery.storage.ShardInbox
import io.spine.delivery.storage.Subscription
import io.spine.delivery.storage.checkPageSize
import io.spine.server.delivery.InboxMessage
import io.spine.server.delivery.InboxMessageId
import io.spine.server.delivery.InboxMessageStatus.TO_DELIVER
import io.spine.server.delivery.ShardIndex
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

/**
 * An [InboxStore] that keeps the messages in the memory of the process.
 *
 * The messages of each shard are kept in a [ShardInbox], locked on its own, so operations
 * on different shards never wait for each other. The inbox of a shard is created on the first
 * write to the shard, and is never removed, so a write never races with a removal.
 *
 * The stored messages are the written objects themselves, and reads return them as they are,
 * which is safe because Protobuf messages are immutable.
 *
 * The listeners passed to [subscribe] are called on the thread that made the change, after
 * the lock of the shard is released.
 */
public class InMemoryInboxStore : InboxStore {

    /**
     * The messages of each shard that has ever been written to.
     */
    private val inboxes = ConcurrentHashMap<ShardIndex, ShardInbox<InboxMessage>>()

    /**
     * The listeners of the changes of the shards.
     */
    private val listeners = ChangeListeners()

    /**
     * Stores the given messages, each in the shard of its own ID.
     *
     * The messages of each shard are stored under one lock, and then the shard is reported
     * as changed.
     */
    override fun write(messages: Iterable<InboxMessage>) {
        for ((shard, batch) in messages.groupBy { it.id.index }) {
            val inbox = inboxes.computeIfAbsent(shard) { ShardInbox(it, InboxMessageForm) }
            synchronized(inbox) {
                batch.forEach(inbox::put)
            }
            listeners.changed(shard)
        }
    }

    /**
     * Removes the messages with the given IDs.
     *
     * The messages of each shard are removed under one lock, and then the shard is reported
     * as changed, if any of its messages was removed.
     */
    override fun delete(ids: Iterable<InboxMessageId>) {
        for ((shard, batch) in ids.groupBy { it.index }) {
            val inbox = inboxes[shard] ?: continue
            var removed = false
            synchronized(inbox) {
                for (id in batch) {
                    removed = inbox.remove(id.uuid) || removed
                }
            }
            if (removed) {
                listeners.changed(shard)
            }
        }
    }

    /**
     * Returns the message with the given ID, or `null` if there is none.
     */
    override fun find(id: InboxMessageId): InboxMessage? {
        val inbox = inboxes[id.index] ?: return null
        return synchronized(inbox) { inbox.find(id.uuid) }
    }

    /**
     * Returns at most [pageSize] messages of the shard, in the order of their order keys:
     * those received strictly after [since], or from the start of the shard, if [since]
     * is `null`.
     *
     * @throws IllegalArgumentException if [pageSize] is not positive.
     */
    override fun page(shard: ShardIndex, since: Timestamp?, pageSize: Int): List<InboxMessage> {
        checkPageSize(pageSize)
        val inbox = inboxes[shard] ?: return emptyList()
        return synchronized(inbox) { inbox.page(since, pageSize) }
    }

    /**
     * Returns the message of the shard in the `TO_DELIVER` status that was received last,
     * or `null` if there is none.
     */
    override fun newestToDeliver(shard: ShardIndex): InboxMessage? {
        val inbox = inboxes[shard] ?: return null
        return synchronized(inbox) { inbox.newestToDeliver() }
    }

    /**
     * Returns the number of messages in the shard.
     */
    override fun count(shard: ShardIndex): Int {
        val inbox = inboxes[shard] ?: return 0
        return synchronized(inbox) { inbox.size }
    }

    /**
     * Returns the number of messages in each of the given shards, including those that hold
     * none.
     */
    override fun count(shards: Collection<ShardIndex>): Map<ShardIndex, Int> =
        shards.associateWith { count(it) }

    /**
     * Returns the number of messages of every shard that holds at least one.
     */
    override fun counts(): Map<ShardIndex, Int> {
        val result = HashMap<ShardIndex, Int>()
        for ((shard, inbox) in inboxes) {
            val size = synchronized(inbox) { inbox.size }
            if (size > 0) {
                result[shard] = size
            }
        }
        return result
    }

    /**
     * Calls [onChange] with the shard of every write, and of every delete that removes
     * a message, on the thread that made the change.
     */
    override fun subscribe(onChange: Consumer<ShardIndex>): Subscription = listeners.add(onChange)

    /**
     * Removes the listeners passed to [subscribe]. The messages stay readable.
     */
    override fun close() {
        listeners.clear()
    }
}

/**
 * Reads the order key and the status of an `InboxMessage` stored as it is.
 */
private object InboxMessageForm : MessageForm<InboxMessage>() {

    override fun uuid(message: InboxMessage): String = message.id.uuid

    override fun seconds(message: InboxMessage): Long = message.whenReceived.seconds

    override fun nanos(message: InboxMessage): Int = message.whenReceived.nanos

    override fun version(message: InboxMessage): Int = message.version

    override fun isToDeliver(message: InboxMessage): Boolean = message.status == TO_DELIVER
}
