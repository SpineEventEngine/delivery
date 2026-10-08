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

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.spine.delivery.storage.given.session
import io.spine.delivery.storage.given.shard
import io.spine.server.delivery.ShardSessionRecord
import java.time.Duration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The contract of [ShardSessionStore], which every backend passes.
 *
 * A subclass creates a new, empty store for each test.
 */
public abstract class ShardSessionStoreContract {

    /**
     * Creates a new store that holds no records.
     */
    protected abstract fun newStore(): ShardSessionStore

    /**
     * How long to wait for a change notification that must arrive.
     */
    protected open val changeTimeout: Duration = Duration.ofSeconds(10)

    /**
     * How long to wait to make sure that no other change notification arrives.
     *
     * Zero for a store that notifies before its operation returns.
     */
    protected open val quietPeriod: Duration = Duration.ZERO

    private lateinit var store: ShardSessionStore

    private val first = shard(1)
    private val second = shard(2)
    private val third = shard(3)

    @BeforeEach
    public fun createStore() {
        store = newStore()
    }

    @AfterEach
    public fun closeStore() {
        store.close()
    }

    private fun create(record: ShardSessionRecord): Stored {
        store.compareAndSet(record.index, null, record) shouldBe CasOutcome.Applied
        return store.read(record.index).shouldNotBeNull()
    }

    private fun CasOutcome.shouldConflictWith(record: ShardSessionRecord?) {
        val conflict = shouldBeInstanceOf<CasOutcome.Conflict>()
        conflict.current?.record shouldBe record
    }

    @Nested
    @DisplayName("read records")
    public inner class Read {

        @Test
        public fun `of an empty store`() {
            store.read(first).shouldBeNull()
            store.read(listOf(first, second)) shouldBe emptyMap()
            store.readAll().shouldBeEmpty()
        }

        @Test
        public fun `of the given shards that have one`() {
            val inFirst = session(first)
            val inSecond = session(second, worker = "other")
            create(inFirst)
            create(inSecond)

            store.read(first)?.record shouldBe inFirst
            store.read(listOf(first, second, third)).mapValues { it.value.record } shouldBe
                    mapOf(first to inFirst, second to inSecond)
            store.readAll().map { it.record } shouldContainExactlyInAnyOrder
                    listOf(inFirst, inSecond)
        }
    }

    @Nested
    @DisplayName("compare and set a record")
    public inner class CompareAndSet {

        @Test
        public fun `creating it when none is expected`() {
            val record = session(first)

            store.compareAndSet(first, null, record) shouldBe CasOutcome.Applied

            store.read(first)?.record shouldBe record
            store.read(second).shouldBeNull()
        }

        @Test
        public fun `rejecting creation when there is a record`() {
            val existing = session(first)
            create(existing)

            store.compareAndSet(first, null, session(first, worker = "other"))
                .shouldConflictWith(existing)
            store.read(first)?.record shouldBe existing
        }

        @Test
        public fun `replacing the expected record`() {
            val stored = create(session(first))
            val replacement = session(first, worker = "other", pickedAt = 1)

            store.compareAndSet(first, stored, replacement) shouldBe CasOutcome.Applied

            store.read(first)?.record shouldBe replacement
        }

        @Test
        public fun `rejecting a stale expected record`() {
            val stale = create(session(first))
            val current = session(first, worker = "current", pickedAt = 1)
            store.compareAndSet(first, stale, current) shouldBe CasOutcome.Applied

            store.compareAndSet(first, stale, session(first, worker = "late", pickedAt = 2))
                .shouldConflictWith(current)
            store.read(first)?.record shouldBe current
        }

        @Test
        public fun `rejecting an expected record when there is none`() {
            val stored = create(session(first))

            store.compareAndSet(second, stored, session(second)).shouldConflictWith(null)
            store.read(second).shouldBeNull()
        }

        @Test
        public fun `executed twice in a row, recognizing its own write`() {
            val stored = create(session(first))
            val replacement = session(first, worker = "other", pickedAt = 1)
            store.compareAndSet(first, stored, replacement) shouldBe CasOutcome.Applied

            val repeated = store.compareAndSet(first, stored, replacement)

            val conflict = repeated.shouldBeInstanceOf<CasOutcome.Conflict>()
            conflict.current.shouldNotBeNull().holds(replacement) shouldBe true
            conflict.current.shouldNotBeNull().holds(session(first)) shouldBe false
            store.read(first)?.record shouldBe replacement
        }
    }

    @Nested
    @DisplayName("report a change of a shard")
    public inner class Changes {

        private lateinit var changes: ChangeRecorder
        private lateinit var subscription: Subscription

        @BeforeEach
        public fun subscribe() {
            changes = ChangeRecorder(changeTimeout, quietPeriod)
            subscription = store.subscribe(changes)
        }

        @Test
        public fun `after an applied write`() {
            val stored = create(session(first))
            changes.expect(first)

            store.compareAndSet(first, stored, session(first, worker = "other"))
            changes.expect(first)
        }

        @Test
        public fun `only if the write is applied`() {
            create(session(first))
            changes.expect(first)

            store.compareAndSet(first, null, session(first, worker = "other"))
            changes.expectNone()
        }

        @Test
        public fun `only while subscribed`() {
            subscription.cancel()

            create(session(first))
            changes.expectNone()
        }
    }

}
