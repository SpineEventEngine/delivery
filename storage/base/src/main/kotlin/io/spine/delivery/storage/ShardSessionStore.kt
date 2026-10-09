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
import java.util.UUID
import java.util.function.Consumer

/**
 * Stores the session records of shards: which worker processes a shard, and since when.
 *
 * The only way to write a record is [compareAndSet], which writes it only if the stored
 * record has not changed since it was read. So when several processes share the store,
 * no two of them can change a record based on the same stored value.
 *
 * Each record is stored with the ID of the write that stored it. A writer that did not
 * learn the outcome of its write tells by that ID whether the record it finds is its own,
 * even when another writer stored an equal record.
 */
public interface ShardSessionStore : AutoCloseable {

    /**
     * Returns the record of the shard, or `null` if there is none.
     */
    public fun read(shard: ShardIndex): Stored?

    /**
     * Returns the records of those of the given shards that have one.
     */
    public fun read(shards: Collection<ShardIndex>): Map<ShardIndex, Stored>

    /**
     * Returns all the records.
     */
    public fun readAll(): List<Stored>

    /**
     * Writes [replacement] with [writeId] as the record of the shard, if the stored record
     * is still the [expected] one, or if there is no record and [expected] is `null`.
     *
     * The stored record is the expected one only if both the record and the write ID are
     * the same. So a record that an equal record of another write replaced does not
     * match.
     *
     * @param shard The shard whose record to write.
     * @param expected The record as it was read, or `null` if there was none.
     * @param replacement The record to write.
     * @param writeId The ID of the write, which is stored with the record. All attempts of
     *   one write pass the same ID, and different writes pass different IDs.
     * @return [CasOutcome.Applied] if the replacement was written, or [CasOutcome.Conflict]
     *   with the current record otherwise.
     */
    public fun compareAndSet(
        shard: ShardIndex,
        expected: Stored?,
        replacement: ShardSessionRecord,
        writeId: UUID
    ): CasOutcome

    /**
     * Calls [onChange] with the shard of every written record, after the write is applied,
     * whichever process sharing the store wrote it.
     *
     * The listener is called on the thread that wrote the record, or on a thread of
     * the client of the database, so it must not block.
     */
    public fun subscribe(onChange: Consumer<ShardIndex>): Subscription

    /**
     * Calls [onMissed] whenever changes may have been made without being reported to
     * the listeners passed to [subscribe], for example after a connection to the backend
     * is established again.
     *
     * A store that reports every change, such as one in memory, never calls it.
     * The listener must not block.
     *
     * @return The subscription that stops the calls.
     */
    public fun subscribeToMissedChanges(onMissed: Runnable): Subscription = Subscription {}

    /**
     * Releases the subscriptions and the resources of the store.
     */
    override fun close()
}

/**
 * A shard session record together with the ID of the write that stored it, and the exact
 * form in which a store keeps them.
 *
 * [ShardSessionStore.compareAndSet] compares the stored forms, not the records. So to
 * write a record, pass the `Stored` that was read, never one rebuilt from its record.
 *
 * @property record The stored record.
 * @property writeId The ID of the write that stored the record.
 * @property form The form in which the store keeps the record and the write ID, such as
 *   their serialized bytes, or the record itself.
 */
public class Stored(
    public val record: ShardSessionRecord,
    public val writeId: UUID,
    public val form: Any
) {

    /**
     * Returns a string with the record and the write ID, leaving out the stored form,
     * which is usually just bytes.
     */
    override fun toString(): String = "Stored(record=$record, writeId=$writeId)"
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
     * @property current The record stored at the moment of the attempt, or `null` if
     *   there was none.
     */
    public class Conflict(public val current: Stored?) : CasOutcome
}
