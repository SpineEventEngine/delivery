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

package io.spine.delivery.server.grpc

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.spine.delivery.admin.ShardInfoUpdates.currentState
import io.spine.delivery.admin.grpc.ShardStatus.NOT_PICKED
import io.spine.delivery.server.given.ObservedInboxStore
import io.spine.delivery.server.given.RecordingObserver
import io.spine.delivery.storage.given.message
import io.spine.delivery.storage.given.session
import io.spine.delivery.storage.given.shard
import io.spine.delivery.storage.given.time
import io.spine.delivery.storage.memory.InMemoryInboxStore
import io.spine.delivery.storage.memory.InMemoryShardSessionStore
import io.spine.server.delivery.ShardIndex
import io.spine.server.delivery.ShardSessionRecord
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.MILLISECONDS
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * An interval long enough for the changes a test makes to fall within it.
 */
private val LONG_INTERVAL: Duration = Duration.ofSeconds(2)

/**
 * How long a test waits for something that must happen.
 */
private const val WAIT_MILLIS = 10_000L

@DisplayName("`ShardUpdateSender` should")
internal class ShardUpdateSenderSpec {

    private val inbox = ObservedInboxStore(InMemoryInboxStore())
    private val sessions = InMemoryShardSessionStore()
    private val senders = ArrayList<ShardUpdateSender>()

    private val first = shard(1)
    private val second = shard(2)
    private val third = shard(3)

    @AfterEach
    fun closeSenders() {
        senders.forEach { it.close() }
    }

    private fun sender(interval: Duration = Duration.ZERO): ShardUpdateSender =
        ShardUpdateSender(inbox, sessions, interval).also { senders.add(it) }

    private fun ShardUpdateSender.subscribed(
        observer: RecordingObserver = RecordingObserver()
    ): RecordingObserver {
        subscribe(observer)
        return observer
    }

    private fun write(shard: ShardIndex, count: Int = 1) =
        inbox.write((1..count).map { message(shard) })

    private fun state(shard: ShardIndex, count: Int, session: ShardSessionRecord? = null) =
        currentState(shard, session, count)

    private fun awaitShardReads(count: Int) {
        val deadline = System.currentTimeMillis() + WAIT_MILLIS
        while (inbox.shardReads.get() < count) {
            check(System.currentTimeMillis() < deadline) { "Expected $count reads." }
            Thread.sleep(1)
        }
    }

    @Nested
    @DisplayName("serve a new subscriber")
    inner class NewSubscriber {

        @Test
        fun `with the acknowledgment first`() {
            write(first)

            val observer = sender().subscribed()

            observer.nextResponse().created shouldBe true
            observer.nextUpdate() shouldBe state(first, 1)
        }

        @Test
        fun `with the current state of every known shard`() {
            write(first, count = 2)
            val picked = session(second, pickedAt = 5)
            sessions.compareAndSet(second, null, picked)
            val released = session(third, pickedAt = 7).toBuilder().clearWorker().build()
            sessions.compareAndSet(third, null, released)

            val observer = sender().subscribed()

            observer.nextUpdates(3) shouldContainExactlyInAnyOrder listOf(
                state(first, 2),
                state(second, 0, picked),
                state(third, 0, released)
            )
            observer.expectNoUpdate()
        }

        @Test
        fun `with a state that another subscriber has received`() {
            val sender = sender()
            val earlier = sender.subscribed()
            write(first)
            earlier.nextUpdate() shouldBe state(first, 1)

            val later = sender.subscribed()

            later.nextUpdate() shouldBe state(first, 1)
            later.expectNoUpdate()
        }

        @Test
        fun `with a zero count of a shard that another subscriber last received with messages`() {
            val sender = sender(LONG_INTERVAL)
            val earlier = sender.subscribed()
            val message = message(first)
            inbox.write(listOf(message))
            earlier.nextUpdate() shouldBe state(first, 1)
            inbox.delete(listOf(message.id))

            val later = sender.subscribed()

            later.nextUpdate() shouldBe state(first, 0)
        }

        @Test
        fun `without repeating its initial state`() {
            val message = message(first)
            inbox.write(listOf(message))
            val sender = sender()

            val observer = sender.subscribed()
            inbox.write(listOf(message))
            observer.nextUpdate() shouldBe state(first, 1)
            write(first)

            observer.nextUpdate() shouldBe state(first, 2)
        }

        @Test
        fun `ending at the current state when the shard changes during the subscription`() {
            val sender = sender()
            val writes = 200
            val writer = Executors.newSingleThreadExecutor()
            try {
                val done = writer.submit { repeat(writes) { write(first) } }
                val observer = sender.subscribed()
                done.get(WAIT_MILLIS, MILLISECONDS)

                val received = observer.awaitUpdate(state(first, writes))

                received.zipWithNext().none { (a, b) -> a == b } shouldBe true
            } finally {
                writer.shutdownNow()
            }
        }

        @Test
        fun `after retrying a failed read`() {
            write(first)
            inbox.failingAllReads.set(2)

            val observer = sender().subscribed()

            observer.nextUpdate() shouldBe state(first, 1)
            inbox.allReads.get() shouldBeGreaterThanOrEqual 3
        }
    }

    @Nested
    @DisplayName("throttle the updates")
    inner class Throttling {

        @Test
        fun `sending the first change at once, and merging the rest of the interval`() {
            val observer = sender(LONG_INTERVAL).subscribed()
            val start = System.nanoTime()

            write(first)
            observer.nextUpdate() shouldBe state(first, 1)
            System.nanoTime() - start shouldBeLessThan LONG_INTERVAL.toNanos()
            repeat(3) { write(first) }

            observer.nextUpdate() shouldBe state(first, 4)
            System.nanoTime() - start shouldBeGreaterThanOrEqual LONG_INTERVAL.toNanos()
        }

        @Test
        fun `of each shard independently`() {
            val observer = sender(LONG_INTERVAL).subscribed()
            write(first)
            observer.nextUpdate() shouldBe state(first, 1)

            write(first)
            write(second)

            observer.nextUpdate() shouldBe state(second, 1)
            observer.nextUpdate() shouldBe state(first, 2)
        }

        @Test
        fun `not at all with a zero interval`() {
            val observer = sender().subscribed()

            for (count in 1..5) {
                write(first)
                observer.nextUpdate() shouldBe state(first, count)
            }
        }
    }

    @Nested
    @DisplayName("send")
    inner class Send {

        @Test
        fun `the full state of the shard, including a count of zero`() {
            val observer = sender().subscribed()
            val picked = session(first, pickedAt = 10)
            sessions.compareAndSet(first, null, picked)
            observer.nextUpdate() shouldBe state(first, 0, picked)

            val messages = listOf(message(first), message(first))
            inbox.write(messages)
            observer.nextUpdate() shouldBe state(first, 2, picked)

            val released = picked.toBuilder().clearWorker().build()
            sessions.compareAndSet(first, sessions.read(first), released)
            observer.nextUpdate() shouldBe state(first, 2, released)

            inbox.delete(messages.map { it.id })
            val drained = observer.nextUpdate()
            drained shouldBe state(first, 0, released)
            drained.newStatus shouldBe NOT_PICKED
            drained.whenLastPicked shouldBe time(10)
            drained.newMessagesCount shouldBe 0
        }

        @Test
        fun `nothing for a state that the subscriber received last`() {
            val observer = sender().subscribed()
            val message = message(first)
            inbox.write(listOf(message))
            observer.nextUpdate() shouldBe state(first, 1)
            val reads = inbox.shardReads.get()

            inbox.write(listOf(message))
            awaitShardReads(reads + 1)
            write(first)

            observer.nextUpdate() shouldBe state(first, 2)
        }

        @Test
        fun `nothing for a change back to the state that the subscriber received last`() {
            val observer = sender(LONG_INTERVAL).subscribed()
            write(first)
            observer.nextUpdate() shouldBe state(first, 1)
            val reads = inbox.shardReads.get()

            val transient = message(first)
            inbox.write(listOf(transient))
            inbox.delete(listOf(transient.id))
            awaitShardReads(reads + 1)
            write(first)

            observer.nextUpdate() shouldBe state(first, 2)
        }

        @Test
        fun `the current state at the end, under concurrent writers`() {
            val observer = sender().subscribed()
            val writers = Executors.newFixedThreadPool(4)
            try {
                val tasks = (1..4).map {
                    writers.submit {
                        repeat(50) {
                            val message = message(first)
                            inbox.write(listOf(message))
                            if (it % 2 == 0) {
                                inbox.delete(listOf(message.id))
                            }
                        }
                    }
                }
                tasks.forEach { it.get(WAIT_MILLIS, MILLISECONDS) }

                inbox.count(first) shouldBe 100
                val received = observer.awaitUpdate(state(first, 100))

                received.zipWithNext().none { (a, b) -> a == b } shouldBe true
            } finally {
                writers.shutdownNow()
            }
        }

        @Test
        fun `an update after retrying a failed read`() {
            val observer = sender().subscribed()
            inbox.failingShardReads.set(2)

            write(first)

            observer.nextUpdate() shouldBe state(first, 1)
            inbox.shardReads.get() shouldBeGreaterThanOrEqual 3
        }

        @Test
        fun `the states of the other shards when one shard cannot be expressed`() {
            val sender = sender()
            val observer = sender.subscribed()

            inbox.write(listOf(message(ShardIndex.getDefaultInstance())))
            write(first)
            observer.awaitUpdate(state(first, 1))

            val later = sender.subscribed()
            later.nextUpdate() shouldBe state(first, 1)
            later.expectNoUpdate()
        }

        @Test
        fun `to the other subscribers when sending to one fails`() {
            val sender = sender()
            val failing = sender.subscribed(RecordingObserver(failingUpdates = true))
            val healthy = sender.subscribed()

            write(first)
            healthy.nextUpdate() shouldBe state(first, 1)
            write(first)
            healthy.nextUpdate() shouldBe state(first, 2)

            failing.error.shouldNotBeNull()
        }
    }

    @Nested
    @DisplayName("after a store reports missed changes, send the current state")
    inner class MissedChanges {

        @Test
        fun `of a shard emptied in the meantime`() {
            val observer = sender().subscribed()
            val message = message(first)
            inbox.write(listOf(message))
            observer.nextUpdate() shouldBe state(first, 1)
            inbox.muted = true
            inbox.delete(listOf(message.id))
            inbox.muted = false

            inbox.reportMissedChanges()

            observer.nextUpdate() shouldBe state(first, 0)
        }

        @Test
        fun `of a shard changed in the meantime`() {
            val observer = sender().subscribed()
            write(first)
            observer.nextUpdate() shouldBe state(first, 1)
            inbox.muted = true
            write(first)
            inbox.muted = false

            inbox.reportMissedChanges()

            observer.nextUpdate() shouldBe state(first, 2)
        }
    }

    @Nested
    @DisplayName("read nothing")
    inner class NoReads {

        @Test
        fun `while there are no subscribers`() {
            val sender = sender()
            write(first)
            val observer = sender.subscribed()
            observer.nextUpdate() shouldBe state(first, 1)
            observer.cancel()
            val reads = inbox.shardReads.get() + inbox.allReads.get()

            write(first)
            write(second)
            observer.expectNoUpdate()

            inbox.shardReads.get() + inbox.allReads.get() shouldBe reads
        }

        @Test
        fun `after it is closed`() {
            val sender = sender()
            val observer = sender.subscribed()
            observer.nextResponse().created shouldBe true
            sender.close()
            val reads = inbox.shardReads.get() + inbox.allReads.get()

            write(first)
            observer.expectNoUpdate()

            inbox.shardReads.get() + inbox.allReads.get() shouldBe reads
        }
    }
}
