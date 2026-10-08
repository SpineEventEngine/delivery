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

package io.spine.delivery.storage.hazelcast

import com.google.protobuf.Timestamp
import com.hazelcast.core.EntryEvent
import com.hazelcast.map.IMap
import com.hazelcast.map.listener.EntryAddedListener
import com.hazelcast.map.listener.EntryRemovedListener
import com.hazelcast.map.listener.EntryUpdatedListener
import io.spine.delivery.storage.ChangeListeners
import io.spine.delivery.storage.InboxStore
import io.spine.delivery.storage.MissedChangeListeners
import io.spine.delivery.storage.Subscription
import io.spine.delivery.storage.checkPageSize
import io.spine.delivery.storage.parseMessage
import io.spine.delivery.storage.shardOf
import io.spine.delivery.storage.tag
import io.spine.server.delivery.InboxMessage
import io.spine.server.delivery.InboxMessageId
import io.spine.server.delivery.ShardIndex
import java.util.function.Consumer

/**
 * An [InboxStore] kept in the Hazelcast map [INBOX_MAP], one entry per shard.
 *
 * Every read or change of messages is an entry processor that runs on the member owning
 * the shard: one network round trip, atomic with respect to every other operation on
 * that shard.
 * The map is used only through processors and listeners, so that whole shards are never
 * serialized for a read.
 */
public class HazelcastInboxStore internal constructor(
    private val map: IMap<String, HazelcastShard?>,
    private val missed: MissedChangeListeners
) : InboxStore {

    private val listeners = ChangeListeners()
    private val listenerId = map.addEntryListener(ShardChanges(listeners), false)

    override fun write(messages: Iterable<InboxMessage>) {
        for ((shard, batch) in messages.groupBy { it.id.index }) {
            val held = batch.map(HeldMessage::of)
            map.executeOnKey(shard.tag(), WriteMessages(shard, held))
        }
    }

    override fun delete(ids: Iterable<InboxMessageId>) {
        for ((shard, batch) in ids.groupBy { it.index }) {
            map.executeOnKey(shard.tag(), DeleteMessages(batch.map { it.uuid }))
        }
    }

    override fun find(id: InboxMessageId): InboxMessage? =
        map.executeOnKey(id.index.tag(), FindMessage(id.uuid))?.let(::parseMessage)

    override fun page(shard: ShardIndex, since: Timestamp?, pageSize: Int): List<InboxMessage> {
        checkPageSize(pageSize)
        return map.executeOnKey(shard.tag(), ReadPage(since, pageSize)).map(::parseMessage)
    }

    override fun newestToDeliver(shard: ShardIndex): InboxMessage? =
        map.executeOnKey(shard.tag(), FindNewestToDeliver())?.let(::parseMessage)

    override fun count(shard: ShardIndex): Int =
        map.executeOnKey(shard.tag(), CountMessages()) ?: 0

    override fun count(shards: Collection<ShardIndex>): Map<ShardIndex, Int> {
        if (shards.isEmpty()) {
            return emptyMap()
        }
        val counts = map.executeOnKeys(shards.mapTo(HashSet()) { it.tag() }, CountMessages())
        return shards.associateWith { counts[it.tag()] ?: 0 }
    }

    override fun counts(): Map<ShardIndex, Int> {
        val result = HashMap<ShardIndex, Int>()
        for ((tag, count) in map.executeOnEntries(CountMessages())) {
            if (count != null && count > 0) {
                result[shardOf(tag)] = count
            }
        }
        return result
    }

    override fun subscribe(onChange: Consumer<ShardIndex>): Subscription = listeners.add(onChange)

    override fun subscribeToMissedChanges(onMissed: Runnable): Subscription = missed.add(onMissed)

    override fun close() {
        try {
            map.removeEntryListener(listenerId)
        } finally {
            listeners.clear()
        }
    }
}

/**
 * Reports the shard of every event of a map keyed by shard tags.
 */
internal class ShardChanges(
    private val listeners: ChangeListeners
) : EntryAddedListener<String, Any?>,
    EntryUpdatedListener<String, Any?>,
    EntryRemovedListener<String, Any?> {

    override fun entryAdded(event: EntryEvent<String, Any?>) = changed(event)

    override fun entryUpdated(event: EntryEvent<String, Any?>) = changed(event)

    override fun entryRemoved(event: EntryEvent<String, Any?>) = changed(event)

    private fun changed(event: EntryEvent<String, Any?>) {
        listeners.changed(shardOf(event.key))
    }
}
