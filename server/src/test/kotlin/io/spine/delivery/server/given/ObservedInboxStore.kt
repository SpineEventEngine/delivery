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

package io.spine.delivery.server.given

import io.spine.delivery.storage.InboxStore
import io.spine.delivery.storage.MissedChangeListeners
import io.spine.delivery.storage.Subscription
import io.spine.server.delivery.ShardIndex
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

/**
 * An inbox store that counts the reads of message counts, can fail them, and can miss
 * changes, as a store shared by several processes does.
 *
 * @param delegate The store that keeps the messages.
 */
internal class ObservedInboxStore(
    private val delegate: InboxStore
) : InboxStore by delegate {

    /**
     * The number of reads of the counts of given shards, failed ones included.
     */
    val shardReads = AtomicInteger()

    /**
     * The number of reads of the counts of all shards, failed ones included.
     */
    val allReads = AtomicInteger()

    /**
     * The number of the next reads of the counts of given shards that fail.
     */
    val failingShardReads = AtomicInteger()

    /**
     * The number of the next reads of the counts of all shards that fail.
     */
    val failingAllReads = AtomicInteger()

    /**
     * Whether the changes are not reported to the subscribers.
     */
    @Volatile
    var muted = false

    /**
     * The listeners of missed changes, called by [reportMissedChanges].
     */
    private val missed = MissedChangeListeners()

    override fun subscribe(onChange: Consumer<ShardIndex>): Subscription =
        delegate.subscribe { if (!muted) onChange.accept(it) }

    override fun subscribeToMissedChanges(onMissed: Runnable): Subscription = missed.add(onMissed)

    /**
     * Tells the subscribers that changes may have been missed.
     */
    fun reportMissedChanges() {
        missed.missed()
    }

    override fun count(shards: Collection<ShardIndex>): Map<ShardIndex, Int> {
        shardReads.incrementAndGet()
        failIfAsked(failingShardReads)
        return delegate.count(shards)
    }

    override fun counts(): Map<ShardIndex, Int> {
        allReads.incrementAndGet()
        failIfAsked(failingAllReads)
        return delegate.counts()
    }

    /**
     * Throws if the given number of the next failing reads is positive, and decrements it.
     */
    private fun failIfAsked(failing: AtomicInteger) {
        if (failing.getAndUpdate { if (it > 0) it - 1 else 0 } > 0) {
            error("The read failed.")
        }
    }
}
