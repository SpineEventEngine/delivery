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
import kotlin.uuid.Uuid

/**
 * A store whose first write meets a record that another process changed right before it,
 * after the writer read the record.
 *
 * Not thread-safe: the tests that race their writes call the store from one thread.
 *
 * @param delegate The store that keeps the records. The other process changes them
 *   directly, without going through this store.
 * @param beforeFirstWrite Changes the record of the given shard right before the first
 *   write is applied.
 */
internal class RacingShardSessionStore(
    private val delegate: ShardSessionStore,
    private val beforeFirstWrite: (ShardIndex) -> Unit
) : ShardSessionStore by delegate {

    /**
     * Whether the first write was attempted.
     */
    private var raced = false

    override fun compareAndSet(
        shard: ShardIndex,
        expected: Stored?,
        replacement: ShardSessionRecord,
        writeId: Uuid
    ): CasOutcome {
        if (!raced) {
            raced = true
            beforeFirstWrite(shard)
        }
        return delegate.compareAndSet(shard, expected, replacement, writeId)
    }
}
