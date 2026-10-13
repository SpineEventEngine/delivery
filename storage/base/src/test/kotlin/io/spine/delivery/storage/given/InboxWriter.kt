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

package io.spine.delivery.storage.given

import io.spine.delivery.storage.InboxStore
import io.spine.server.delivery.InboxMessage
import io.spine.server.delivery.InboxMessageStatus.DELIVERED
import io.spine.server.delivery.InboxMessageStatus.TO_DELIVER
import io.spine.server.delivery.ShardIndex
import kotlin.random.Random

/**
 * The upper bound, exclusive, of the seconds at which an [InboxWriter] receives its messages.
 *
 * It is small, so that the messages of different writers are often received at the same
 * second. Each message is received at a whole second, with no nanos.
 */
const val RECEIVE_SECONDS: Long = 50

/**
 * Orders the messages by their order keys, as an [InboxStore] does.
 *
 * The UUIDs are compared as strings. For the ASCII UUIDs of an [InboxWriter], this is the same
 * as the comparison by Unicode code points that a store uses.
 */
val orderKeys: Comparator<InboxMessage> = compareBy(
    { it.whenReceived.seconds },
    { it.whenReceived.nanos },
    { it.version },
    { it.id.uuid }
)

/**
 * Changes the inbox messages of two shards in rounds, and remembers the messages that it leaves
 * in the store.
 *
 * A writer changes only the messages that it has written, so the messages that several writers
 * leave in a store do not depend on how their rounds interleave.
 *
 * The random choices of a writer depend only on its [number], so the writer makes the same
 * changes in every run.
 *
 * This class is not thread-safe: each writer is meant for one thread.
 *
 * @property store The store to change.
 * @property number Tells the UUIDs of the writer from those of the other writers.
 * @property own The shard of most of the new messages.
 * @property next The shard of the rest of the new messages.
 */
class InboxWriter(
    private val store: InboxStore,
    private val number: Int,
    private val own: ShardIndex,
    private val next: ShardIndex
) {

    /**
     * Makes the random choices of the writer.
     */
    private val random = Random(number)

    /**
     * The messages that the writer has written and not deleted yet.
     */
    private val written = ArrayList<InboxMessage>()

    /**
     * The number of messages that the writer has created.
     */
    private var created = 0

    /**
     * The messages that the writer has left in the store, in no particular order.
     */
    val messages: List<InboxMessage>
        get() = written

    /**
     * Makes one round of changes.
     *
     * Writes, in one batch, two new messages to the [own] shard, one to the [next] shard, and
     * a changed copy of a written message. Then deletes a written message.
     */
    fun round() {
        val batch = mutableListOf(create(own), create(own), create(next))
        written += batch
        batch += rewrite()
        store.write(batch)
        store.delete(listOf(removeAny().id))
    }

    /**
     * Creates a message of the shard, received at a random second, with a random version and
     * status.
     */
    private fun create(shard: ShardIndex): InboxMessage {
        created++
        return message(
            shard,
            seconds = random.nextLong(RECEIVE_SECONDS),
            version = random.nextInt(3),
            status = if (random.nextBoolean()) TO_DELIVER else DELIVERED,
            uuid = "$number-$created"
        )
    }

    /**
     * Replaces a random written message with a copy that has the other status, or with a copy
     * received at a random second and having the next `version`.
     *
     * @return The copy.
     */
    private fun rewrite(): InboxMessage {
        val index = random.nextInt(written.size)
        val old = written[index]
        val new = if (random.nextBoolean()) {
            old.withStatus(if (old.status == TO_DELIVER) DELIVERED else TO_DELIVER)
        } else {
            old.receivedAt(seconds = random.nextLong(RECEIVE_SECONDS), version = old.version + 1)
        }
        written[index] = new
        return new
    }

    /**
     * Forgets a random written message.
     *
     * @return The forgotten message.
     */
    private fun removeAny(): InboxMessage {
        val index = random.nextInt(written.size)
        val removed = written[index]
        written[index] = written[written.lastIndex]
        written.removeAt(written.lastIndex)
        return removed
    }
}
