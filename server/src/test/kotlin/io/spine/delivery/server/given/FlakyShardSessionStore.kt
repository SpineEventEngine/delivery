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

import io.spine.delivery.storage.CasOutcome
import io.spine.delivery.storage.ShardSessionStore
import io.spine.delivery.storage.Stored
import io.spine.server.delivery.ShardIndex
import io.spine.server.delivery.ShardSessionRecord
import java.util.UUID

/**
 * A store whose writes fail in the ways that the client of a database may fail.
 *
 * Not thread-safe: the tests that inject failures call the store from one thread.
 *
 * @param delegate The store that keeps the records.
 */
internal class FlakyShardSessionStore(
    private val delegate: ShardSessionStore
) : ShardSessionStore by delegate {

    /**
     * The number of the next writes that are applied, after which their reply is lost.
     */
    var lostReplies = 0

    /**
     * The number of the next writes that fail without being applied at once; each of them
     * is applied later, right before the next write.
     */
    var lateWrites = 0

    /**
     * Whether every write reports a conflict with a record that keeps changing.
     */
    var alwaysConflicting = false

    /**
     * Whether reads fail.
     */
    var failingReads = false

    /**
     * The number of the writes attempted through this store.
     */
    var writes = 0
        private set

    /**
     * The writes that failed without being applied, to be applied right before the next
     * write.
     */
    private val pending = ArrayList<() -> Unit>()

    override fun read(shard: ShardIndex): Stored? {
        check(!failingReads) { "The read failed." }
        return delegate.read(shard)
    }

    override fun compareAndSet(
        shard: ShardIndex,
        expected: Stored?,
        replacement: ShardSessionRecord,
        writeId: UUID
    ): CasOutcome {
        writes++
        pending.forEach { it() }
        pending.clear()
        if (alwaysConflicting) {
            val changing = replacement.toBuilder().clearWorker().buildPartial()
            return CasOutcome.Conflict(Stored(changing, UUID.randomUUID(), changing))
        }
        if (lateWrites > 0) {
            lateWrites--
            pending.add { delegate.compareAndSet(shard, expected, replacement, writeId) }
            error("The write timed out.")
        }
        val outcome = delegate.compareAndSet(shard, expected, replacement, writeId)
        if (lostReplies > 0) {
            lostReplies--
            error("The reply was lost.")
        }
        return outcome
    }
}
