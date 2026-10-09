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
 * An [InboxStore] kept in a Hazelcast map, with one entry per shard.
 *
 * The key of an entry is the [tag][tag] of a shard, and its value holds all the messages
 * of the shard. Hazelcast spreads the entries over the members of the cluster, which are
 * the processes that share the map. The member that holds an entry is its owner.
 *
 * Every read and change of messages runs as an entry processor: a small piece of code
 * that Hazelcast sends to the owner of the entry, and runs there, one at a time per entry.
 * So each operation takes one network round trip, and is atomic with respect to every other
 * operation on the same shard. A whole shard is never sent over the network to be read.
 *
 * @param map The map of the shards.
 * @param missed The listeners of the changes that the store may have missed, shared with
 *   the other store of the same member.
 */
public class HazelcastInboxStore internal constructor(
    private val map: IMap<String, HazelcastShard?>,
    private val missed: MissedChangeListeners
) : InboxStore {

    /**
     * The listeners passed to [subscribe].
     */
    private val listeners = ChangeListeners()

    /**
     * The ID of the listener of the changes of [map], which tells [listeners] about them.
     *
     * The listener receives the changes made by every member, without the values of
     * the entries, so that no shard is sent over the network to report a change.
     */
    private val listenerId = map.addEntryListener(ShardChanges(listeners), false)

    /**
     * Writes the messages of each shard with one [WriteMessages] processor.
     *
     * The bytes and the order keys of the messages are prepared before, so that the owner
     * of the shard never parses Protobuf.
     */
    override fun write(messages: Iterable<InboxMessage>) {
        for ((shard, batch) in messages.groupBy { it.id.index }) {
            val held = batch.map(HeldMessage::of)
            map.executeOnKey(shard.tag(), WriteMessages(shard, held))
        }
    }

    /**
     * Removes the messages of each shard with one [DeleteMessages] processor.
     */
    override fun delete(ids: Iterable<InboxMessageId>) {
        for ((shard, batch) in ids.groupBy { it.index }) {
            map.executeOnKey(shard.tag(), DeleteMessages(batch.map { it.uuid }))
        }
    }

    /**
     * Finds the bytes of the message with a [FindMessage] processor, and parses them.
     */
    override fun find(id: InboxMessageId): InboxMessage? =
        map.executeOnKey(id.index.tag(), FindMessage(id.uuid))?.let(::parseMessage)

    /**
     * Reads the bytes of the page with a [ReadPage] processor, and parses them.
     */
    override fun page(shard: ShardIndex, since: Timestamp?, pageSize: Int): List<InboxMessage> {
        checkPageSize(pageSize)
        return map.executeOnKey(shard.tag(), ReadPage(since, pageSize)).map(::parseMessage)
    }

    /**
     * Finds the bytes of the message with a [FindNewestToDeliver] processor, and parses them.
     */
    override fun newestToDeliver(shard: ShardIndex): InboxMessage? =
        map.executeOnKey(shard.tag(), FindNewestToDeliver())?.let(::parseMessage)

    /**
     * Counts the messages with a [CountMessages] processor.
     */
    override fun count(shard: ShardIndex): Int =
        map.executeOnKey(shard.tag(), CountMessages()) ?: 0

    /**
     * Counts the messages with [CountMessages] processors, which run on the owners of all
     * the shards at once.
     */
    override fun count(shards: Collection<ShardIndex>): Map<ShardIndex, Int> {
        if (shards.isEmpty()) {
            return emptyMap()
        }
        val counts = map.executeOnKeys(shards.mapTo(HashSet()) { it.tag() }, CountMessages())
        return shards.associateWith { counts[it.tag()] ?: 0 }
    }

    /**
     * Counts the messages with [CountMessages] processors, which run on every entry of
     * the map. A shard without messages has no entry.
     */
    override fun counts(): Map<ShardIndex, Int> {
        val result = HashMap<ShardIndex, Int>()
        for ((tag, count) in map.executeOnEntries(CountMessages())) {
            if (count != null && count > 0) {
                result[shardOf(tag)] = count
            }
        }
        return result
    }

    /**
     * The listener is called on a thread of Hazelcast, for the changes made by every member.
     */
    override fun subscribe(onChange: Consumer<ShardIndex>): Subscription = listeners.add(onChange)

    /**
     * Calls [onMissed] when a member leaves the cluster, when a part of the data is lost,
     * and when the cluster joins again after a network split.
     *
     * Hazelcast may lose the reports of the changes in these cases.
     */
    override fun subscribeToMissedChanges(onMissed: Runnable): Subscription = missed.add(onMissed)

    /**
     * Stops listening to the changes of the map, and removes the listeners passed to
     * [subscribe].
     *
     * The data stays in the cluster.
     */
    override fun close() {
        try {
            map.removeEntryListener(listenerId)
        } finally {
            listeners.clear()
        }
    }
}

/**
 * Tells the given listeners about every added, updated, or removed entry of a map whose
 * keys are [shard tags][tag].
 *
 * @param listeners The listeners to tell about the changed shards.
 */
internal class ShardChanges(
    private val listeners: ChangeListeners
) : EntryAddedListener<String, Any?>,
    EntryUpdatedListener<String, Any?>,
    EntryRemovedListener<String, Any?> {

    /**
     * Tells the listeners about the shard of the added entry.
     */
    override fun entryAdded(event: EntryEvent<String, Any?>) = changed(event)

    /**
     * Tells the listeners about the shard of the updated entry.
     */
    override fun entryUpdated(event: EntryEvent<String, Any?>) = changed(event)

    /**
     * Tells the listeners about the shard of the removed entry.
     */
    override fun entryRemoved(event: EntryEvent<String, Any?>) = changed(event)

    /**
     * Tells the listeners about the shard whose tag is the key of the event.
     */
    private fun changed(event: EntryEvent<String, Any?>) {
        listeners.changed(shardOf(event.key))
    }
}
