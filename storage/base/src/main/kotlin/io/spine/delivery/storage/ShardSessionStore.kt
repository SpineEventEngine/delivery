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

import io.spine.server.delivery.ShardIndex
import io.spine.server.delivery.ShardSessionRecord
import java.util.function.Consumer

/**
 * Stores the session records of the shard registry.
 *
 * Writes go through [compareAndSet] only, so that every read-then-write of the registry
 * is atomic across all the nodes that share the store.
 */
public interface ShardSessionStore : AutoCloseable {

    /**
     * Returns the record of the shard, or `null` if there is none.
     */
    public fun read(shard: ShardIndex): Stored?

    /**
     * Returns the records of the given shards that exist.
     */
    public fun read(shards: Collection<ShardIndex>): Map<ShardIndex, Stored>

    /**
     * Returns all the records.
     */
    public fun readAll(): List<Stored>

    /**
     * Writes [replacement] as the record of the shard, if the stored record is still
     * the [expected] one, or if there is no record and [expected] is `null`.
     *
     * @return [CasOutcome.Applied] if the replacement was written,
     *   or [CasOutcome.Conflict] with the current record otherwise
     */
    public fun compareAndSet(
        shard: ShardIndex,
        expected: Stored?,
        replacement: ShardSessionRecord
    ): CasOutcome

    /**
     * Calls [onChange] with the shard of every written record, written through any node,
     * after the write is applied. The listener must not block.
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
 * A shard session record together with the exact form in which it is stored.
 *
 * [ShardSessionStore.compareAndSet] compares the stored form, so a caller passes back
 * the `Stored` it has read, never a record rebuilt from it.
 *
 * @property record the stored record
 * @property form the stored form: the serialized bytes in the distributed stores,
 *   or the stored instance itself in memory
 */
public class Stored(public val record: ShardSessionRecord, public val form: Any) {

    /**
     * Tells whether this stored record is exactly the given one: byte for byte, if the
     * form is serialized, or by equality otherwise.
     */
    public fun holds(other: ShardSessionRecord): Boolean =
        if (form is ByteArray) {
            form.contentEquals(other.toByteArray())
        } else {
            record == other
        }

    override fun toString(): String = "Stored(record=$record)"
}

/**
 * The outcome of [ShardSessionStore.compareAndSet].
 */
public sealed interface CasOutcome {

    /**
     * The replacement was written.
     */
    public data object Applied : CasOutcome

    /**
     * The stored record differed from the expected one, so nothing was written.
     *
     * @property current the record stored at the moment of the attempt, or `null` if
     *   there was none
     */
    public class Conflict(public val current: Stored?) : CasOutcome
}
