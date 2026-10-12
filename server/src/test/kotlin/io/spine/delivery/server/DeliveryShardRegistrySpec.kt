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

package io.spine.delivery.server

import com.google.protobuf.util.Durations
import com.google.protobuf.util.Timestamps
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.spine.base.Time
import io.spine.delivery.rejection.ShardAlreadyPickedUp
import io.spine.delivery.server.DeliveryShardRegistry.DeliveryShardSession
import io.spine.delivery.server.DeliveryShardRegistry.ReleaseOutcome.HELD_BY_ANOTHER_WORKER
import io.spine.delivery.server.DeliveryShardRegistry.ReleaseOutcome.NOT_PICKED_UP
import io.spine.delivery.server.DeliveryShardRegistry.ReleaseOutcome.RELEASED
import io.spine.delivery.server.given.FlakyShardSessionStore
import io.spine.delivery.server.given.RacingShardSessionStore
import io.spine.delivery.storage.ShardSessionStore
import io.spine.delivery.storage.Stored
import io.spine.delivery.storage.given.shard
import io.spine.delivery.storage.memory.InMemoryShardSessionStore
import io.spine.server.NodeId
import io.spine.server.delivery.ShardIndex
import io.spine.server.delivery.WorkerId
import io.spine.testing.time.FrozenMadHatterParty
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`DeliveryShardRegistry` should")
internal class DeliveryShardRegistrySpec {

    /**
     * The store of the registry, into whose writes the tests inject failures.
     */
    private val store = FlakyShardSessionStore(InMemoryShardSessionStore())

    /**
     * The registry under test, whose sessions never become stale.
     */
    private val registry = DeliveryShardRegistry(store, Durations.ZERO)
    private val first = shard(1)

    @Test
    fun `pick a shard whose write was applied before its reply was lost`() {
        store.lostReplies = 1

        registry.pickUp(first, worker("w"))

        store.read(first).shouldNotBeNull().record.worker shouldBe worker("w")
    }

    @Test
    fun `pick a shard when an earlier attempt of the same call lands late`() {
        registry.pickUp(first, worker("w"))
        registry.releaseShard(first, worker("w"))
        store.lateWrites = 1

        registry.pickUp(first, worker("w"))

        store.read(first).shouldNotBeNull().record.worker shouldBe worker("w")
    }

    @Test
    fun `not pick a shard that the same worker picked at the same time`() {
        Time.setProvider(FrozenMadHatterParty(Time.currentTime()))
        try {
            registry.pickUp(first, worker("w"))

            shouldThrow<ShardAlreadyPickedUp> { registry.pickUp(first, worker("w")) }
        } finally {
            Time.resetProvider()
        }
    }

    @Test
    fun `not pick a shard that the same worker picks at the same time on another server`() {
        Time.setProvider(FrozenMadHatterParty(Time.currentTime()))
        try {
            val shared = InMemoryShardSessionStore()
            val other = DeliveryShardRegistry(shared, Durations.ZERO)
            val racing = RacingShardSessionStore(shared, beforeFirstWrite = {
                other.pickUp(it, worker("w"))
            })
            val registry = DeliveryShardRegistry(racing, Durations.ZERO)

            shouldThrow<ShardAlreadyPickedUp> { registry.pickUp(first, worker("w")) }
        } finally {
            Time.resetProvider()
        }
    }

    @Test
    fun `release a shard that the worker holds`() {
        registry.pickUp(first, worker("w"))

        registry.releaseShard(first, worker("w")) shouldBe RELEASED

        store.read(first).shouldNotBeNull().record.hasWorker() shouldBe false
    }

    @Test
    fun `release a shard whose reply was lost`() {
        registry.pickUp(first, worker("w"))
        store.lostReplies = 1

        registry.releaseShard(first, worker("w")) shouldBe RELEASED

        store.read(first).shouldNotBeNull().record.hasWorker() shouldBe false
    }

    @Test
    fun `not release a shard that another worker holds`() {
        registry.pickUp(first, worker("a"))

        registry.releaseShard(first, worker("b")) shouldBe HELD_BY_ANOTHER_WORKER

        store.read(first).shouldNotBeNull().record.worker shouldBe worker("a")
    }

    @Test
    fun `not let a worker with a stale session release the shard that another worker picked`() {
        val timeout = Durations.fromSeconds(1)
        val expiring = DeliveryShardRegistry(store, timeout)
        val start = Time.currentTime()
        try {
            Time.setProvider(FrozenMadHatterParty(start))
            expiring.pickUp(first, worker("a"))
            val afterTimeout = Timestamps.add(start, Durations.add(timeout, timeout))
            Time.setProvider(FrozenMadHatterParty(afterTimeout))
            expiring.pickUp(first, worker("b"))

            expiring.releaseShard(first, worker("a")) shouldBe HELD_BY_ANOTHER_WORKER

            store.read(first).shouldNotBeNull().record.worker shouldBe worker("b")
            shouldThrow<ShardAlreadyPickedUp> { expiring.pickUp(first, worker("c")) }
        } finally {
            Time.resetProvider()
        }
    }

    @Test
    fun `not release a shard that another worker picks while the release is in progress`() {
        val shared = InMemoryShardSessionStore()
        val other = DeliveryShardRegistry(shared, Durations.ZERO)
        other.pickUp(first, worker("a"))
        val racing = RacingShardSessionStore(shared, beforeFirstWrite = {
            other.releaseShard(it, worker("a"))
            other.pickUp(it, worker("b"))
        })
        val releasing = DeliveryShardRegistry(racing, Durations.ZERO)

        releasing.releaseShard(first, worker("a")) shouldBe HELD_BY_ANOTHER_WORKER

        shared.read(first).shouldNotBeNull().record.worker shouldBe worker("b")
    }

    @Test
    fun `not write when releasing a shard that no worker holds`() {
        val neverPicked = shard(2)
        registry.pickUp(first, worker("w"))
        registry.releaseShard(first, worker("w"))
        val writes = store.writes

        registry.releaseShard(first, worker("w")) shouldBe NOT_PICKED_UP
        registry.releaseShard(neverPicked, worker("w")) shouldBe NOT_PICKED_UP

        store.writes shouldBe writes
    }

    @Test
    fun `complete a session by releasing its shard`() {
        val session = registry.pickUp(first, worker("w")).shouldBeInstanceOf<DeliveryShardSession>()

        session.complete()

        store.read(first).shouldNotBeNull().record.hasWorker() shouldBe false
    }

    @Test
    fun `not release the shard of another worker when completing a session`() {
        val session = registry.pickUp(first, worker("a")).shouldBeInstanceOf<DeliveryShardSession>()
        registry.releaseShard(first, worker("a"))
        registry.pickUp(first, worker("b"))

        session.complete()

        store.read(first).shouldNotBeNull().record.worker shouldBe worker("b")
    }

    @Test
    fun `not report an inactive session released by a write whose reply was lost`() {
        registry.pickUp(first, worker("w"))
        store.lostReplies = 1

        val released = registry.releaseInactiveSessions(Durations.ZERO)

        released.shouldBeEmpty()
        store.read(first).shouldNotBeNull().record.hasWorker() shouldBe false
    }

    @Test
    fun `fail when the record keeps changing`() {
        store.alwaysConflicting = true

        shouldThrow<IllegalStateException> { registry.pickUp(first, worker("w")) }
        store.writes shouldBe DeliveryShardRegistry.MAX_ATTEMPTS
    }

    @Test
    fun `fail when the read after a failed write fails too`() {
        store.lostReplies = 1
        val failingAfterWrite = object : ShardSessionStore by store {
            private var reads = 0
            override fun read(shard: ShardIndex): Stored? =
                if (reads++ == 0) store.read(shard) else error("The read failed.")
        }
        val failing = DeliveryShardRegistry(failingAfterWrite, Durations.ZERO)

        shouldThrow<IllegalStateException> { failing.pickUp(first, worker("w")) }
    }

    @Test
    fun `let exactly one of concurrent picks win`() {
        val registry = DeliveryShardRegistry(InMemoryShardSessionStore(), Durations.ZERO)
        val threads = 8
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val results = (1..threads).map { i ->
                pool.submit(Callable {
                    start.await()
                    try {
                        registry.pickUp(first, worker("w$i"))
                        true
                    } catch (_: ShardAlreadyPickedUp) {
                        false
                    }
                })
            }
            start.countDown()
            results.count { it.get(10, SECONDS) } shouldBe 1
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `report each released session to exactly one of concurrent calls`() {
        val store = InMemoryShardSessionStore()
        val registry = DeliveryShardRegistry(store, Durations.ZERO)
        val shards = (0 until 50).map { shard(it) }
        shards.forEach { registry.pickUp(it, worker("w${it.index}")) }
        val threads = 4
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val results = (1..threads).map {
                pool.submit(Callable {
                    start.await()
                    registry.releaseInactiveSessions(Durations.ZERO)
                })
            }
            start.countDown()
            val released = results.flatMap { it.get(10, SECONDS) }
            released shouldHaveSize shards.size
            released.map { it.index }.toSet() shouldBe shards.toSet()
        } finally {
            pool.shutdownNow()
        }
    }

    /**
     * Creates the ID of a worker with the given name.
     */
    private fun worker(name: String): WorkerId =
        WorkerId.newBuilder()
            .setNodeId(NodeId.newBuilder().setValue("node"))
            .setValue(name)
            .build()
}
