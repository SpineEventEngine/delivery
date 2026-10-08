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
 * Each shard is a [ShardInbox] guarded by its own monitor, so operations on different
 * shards never wait on each other. A shard is created on its first write and is never
 * removed, so no write can race with a removal.
 *
 * The stored messages are the written objects themselves, and reads return them as
 * they are: protobuf messages are immutable.
 */
public class InMemoryInboxStore : InboxStore {

    private val inboxes = ConcurrentHashMap<ShardIndex, ShardInbox<InboxMessage>>()
    private val listeners = ChangeListeners()

    override fun write(messages: Iterable<InboxMessage>) {
        for ((shard, batch) in messages.groupBy { it.id.index }) {
            val inbox = inboxes.computeIfAbsent(shard) { ShardInbox(it, InboxMessageForm) }
            synchronized(inbox) {
                batch.forEach(inbox::put)
            }
            listeners.changed(shard)
        }
    }

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

    override fun find(id: InboxMessageId): InboxMessage? {
        val inbox = inboxes[id.index] ?: return null
        return synchronized(inbox) { inbox.find(id.uuid) }
    }

    override fun page(shard: ShardIndex, since: Timestamp?, pageSize: Int): List<InboxMessage> {
        checkPageSize(pageSize)
        val inbox = inboxes[shard] ?: return emptyList()
        return synchronized(inbox) { inbox.page(since, pageSize) }
    }

    override fun newestToDeliver(shard: ShardIndex): InboxMessage? {
        val inbox = inboxes[shard] ?: return null
        return synchronized(inbox) { inbox.newestToDeliver() }
    }

    override fun count(shard: ShardIndex): Int {
        val inbox = inboxes[shard] ?: return 0
        return synchronized(inbox) { inbox.size }
    }

    override fun count(shards: Collection<ShardIndex>): Map<ShardIndex, Int> =
        shards.associateWith { count(it) }

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

    override fun subscribe(onChange: Consumer<ShardIndex>): Subscription = listeners.add(onChange)

    /**
     * Removes the listeners. The messages stay readable.
     */
    override fun close() {
        listeners.clear()
    }
}

/**
 * Describes an `InboxMessage` held as it is.
 */
private object InboxMessageForm : MessageForm<InboxMessage>() {

    override fun uuid(message: InboxMessage): String = message.id.uuid

    override fun seconds(message: InboxMessage): Long = message.whenReceived.seconds

    override fun nanos(message: InboxMessage): Int = message.whenReceived.nanos

    override fun version(message: InboxMessage): Int = message.version

    override fun isToDeliver(message: InboxMessage): Boolean = message.status == TO_DELIVER
}
