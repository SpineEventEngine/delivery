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
     * Stores the messages of each shard under the lock of the shard, and then reports
     * the shard as changed.
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
     * Removes the messages of each shard under the lock of the shard, and then reports
     * the shard as changed, if any of its messages was removed.
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
     * Looks the message up by its UUID in the inbox of its shard, under the lock of the shard.
     */
    override fun find(id: InboxMessageId): InboxMessage? {
        val inbox = inboxes[id.index] ?: return null
        return synchronized(inbox) { inbox.find(id.uuid) }
    }

    /**
     * Reads the page under the lock of the shard. The page holds the stored messages
     * themselves, not copies of them.
     */
    override fun page(shard: ShardIndex, since: Timestamp?, pageSize: Int): List<InboxMessage> {
        checkPageSize(pageSize)
        val inbox = inboxes[shard] ?: return emptyList()
        return synchronized(inbox) { inbox.page(since, pageSize) }
    }

    /**
     * Takes the last message of the ordered messages to deliver, under the lock of the shard.
     */
    override fun newestToDeliver(shard: ShardIndex): InboxMessage? {
        val inbox = inboxes[shard] ?: return null
        return synchronized(inbox) { inbox.newestToDeliver() }
    }

    /**
     * Returns the number of distinct UUIDs in the inbox of the shard.
     */
    override fun count(shard: ShardIndex): Int {
        val inbox = inboxes[shard] ?: return 0
        return synchronized(inbox) { inbox.size }
    }

    /**
     * Counts the shards one by one, each under its own lock, so the counts of different
     * shards may come from slightly different moments.
     */
    override fun count(shards: Collection<ShardIndex>): Map<ShardIndex, Int> =
        shards.associateWith { count(it) }

    /**
     * Counts every shard that has ever been written to, each under its own lock, and leaves
     * out the empty ones.
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
     * The listener is called on the thread that made the change, after the lock of
     * the shard is released.
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
