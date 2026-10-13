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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.spine.delivery.storage.given.InboxWriter
import io.spine.delivery.storage.given.RECEIVE_SECONDS
import io.spine.delivery.storage.given.id
import io.spine.delivery.storage.given.message
import io.spine.delivery.storage.given.orderKeys
import io.spine.delivery.storage.given.receivedAt
import io.spine.delivery.storage.given.shard
import io.spine.delivery.storage.given.time
import io.spine.delivery.storage.given.withStatus
import io.spine.server.delivery.InboxMessage
import io.spine.server.delivery.InboxMessageStatus.DELIVERED
import io.spine.server.delivery.InboxMessageStatus.TO_CATCH_UP
import io.spine.server.delivery.InboxMessageStatus.TO_DELIVER
import io.spine.server.delivery.ShardIndex
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The size of a payload that brings a message close to the default maximum size of
 * an inbound gRPC message, 4 MiB.
 */
private const val NEAR_MAX_PAYLOAD = 4 * 1024 * 1024 - 64 * 1024

/**
 * The size of a payload of which a few messages exceed the 8 MiB of one Redis chunk.
 */
private const val LARGE_PAYLOAD = 3 * 1024 * 1024

/**
 * The number of messages in a shard larger than a Redis chunk and than 8,000, which
 * Lua's `unpack` cannot take at once.
 */
private const val LARGE_SHARD = 8_500

/**
 * The number of threads that change the messages of a store at once, which is also
 * the number of shards they change.
 */
private const val WRITERS = 6

/**
 * The number of rounds of changes made by each of the [WRITERS].
 */
private const val ROUNDS = 200

/**
 * The size of a page read while the messages change.
 */
private const val READ_PAGE_SIZE = 20

/**
 * How long to wait for each of the threads that change and read a store to finish.
 */
private val CONCURRENCY_TIMEOUT: Duration = Duration.ofMinutes(1)

/**
 * The contract of [InboxStore], which every backend passes.
 *
 * A subclass creates a new, empty store for each test.
 */
abstract class InboxStoreContract {

    /**
     * Creates a new store that holds no messages.
     */
    protected abstract fun newStore(): InboxStore

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

    /**
     * The store under test, created anew for each test.
     */
    private lateinit var store: InboxStore

    private val first = shard(1)
    private val second = shard(2)
    private val third = shard(3)

    @BeforeEach
    fun createStore() {
        store = newStore()
    }

    @AfterEach
    fun closeStore() {
        store.close()
    }

    /**
     * Writes the given messages in one batch.
     */
    private fun write(vararg messages: InboxMessage) = store.write(messages.asList())

    /**
     * Reads all the messages of the shard in one page.
     */
    private fun all(shard: ShardIndex): List<InboxMessage> = store.page(shard, null, Int.MAX_VALUE)

    @Nested
    @DisplayName("find a message")
    inner class Find {

        @Test
        fun `by its ID`() {
            val message = message(first)
            write(message, message(first), message(second))

            store.find(message.id) shouldBe message
        }

        @Test
        fun `with an ID that is absent`() {
            write(message(first))

            store.find(id(first)).shouldBeNull()
            store.find(id(third)).shouldBeNull()
        }

        @Test
        fun `only in the shard of its ID`() {
            val message = message(first)
            write(message)

            store.find(id(second, message.id.uuid)).shouldBeNull()
        }
    }

    @Nested
    @DisplayName("write messages")
    inner class Write {

        @Test
        fun `replacing a message with the same ID`() {
            val original = message(first, seconds = 10)
            val other = message(first, seconds = 20)
            write(original, other)

            val delivered = original.withStatus(DELIVERED)
            write(delivered)
            store.find(original.id) shouldBe delivered
            store.newestToDeliver(first) shouldBe other
            all(first) shouldContainExactly listOf(delivered, other)

            val later = delivered.withStatus(original.status).receivedAt(seconds = 30)
            write(later)
            all(first) shouldContainExactly listOf(other, later)
            store.newestToDeliver(first) shouldBe later

            val lowerVersion = later.receivedAt(seconds = 20, version = -5)
            write(lowerVersion)
            all(first) shouldContainExactly listOf(lowerVersion, other)
            store.count(first) shouldBe 2
        }

        @Test
        fun `keeping the last of the messages with the same ID in a batch`() {
            val message = message(first, version = 1)
            val replacement = message.receivedAt(seconds = 5, version = 2)
            val other = message(second)
            store.write(listOf(message, other, replacement))

            store.find(message.id) shouldBe replacement
            store.count(first) shouldBe 1
            store.count(second) shouldBe 1
        }

        @Test
        fun `of several shards in one batch`() {
            val messages = listOf(message(first), message(second), message(first))
            store.write(messages)

            store.counts() shouldBe mapOf(first to 2, second to 1)
            messages.forEach { store.find(it.id) shouldBe it }
        }

        @Test
        fun `leaving the same state when written twice`() {
            val messages = listOf(message(first, seconds = 2), message(first, seconds = 1))
            store.write(messages)
            store.write(messages)

            all(first) shouldContainExactly messages.reversed()
            store.counts() shouldBe mapOf(first to 2)
        }

        @Test
        fun `in a batch larger than one Redis chunk by count`() {
            val messages = (1..LARGE_SHARD).map { message(first, seconds = it.toLong()) }
            store.write(messages)

            store.count(first) shouldBe LARGE_SHARD
        }

        @Test
        fun `in a batch larger than one Redis chunk by size`() {
            val messages = (1..4).map {
                message(first, seconds = it.toLong(), payloadSize = LARGE_PAYLOAD)
            }
            store.write(messages)

            all(first) shouldContainExactly messages
        }

        @Test
        fun `close to the maximum size of an inbound message`() {
            val message = message(first, payloadSize = NEAR_MAX_PAYLOAD)
            write(message)

            store.find(message.id) shouldBe message
            all(first) shouldContainExactly listOf(message)
        }
    }

    @Nested
    @DisplayName("delete messages")
    inner class Delete {

        @Test
        fun `by their IDs`() {
            val kept = message(first)
            val deleted = message(first)
            val other = message(second)
            write(kept, deleted, other)

            store.delete(listOf(deleted.id, other.id))

            store.find(deleted.id).shouldBeNull()
            store.find(other.id).shouldBeNull()
            all(first) shouldContainExactly listOf(kept)
        }

        @Test
        fun `ignoring absent and repeated IDs`() {
            val message = message(first)
            write(message)

            store.delete(listOf(id(first), message.id, message.id, id(third)))

            store.count(first) shouldBe 0
            store.counts().shouldBeEmpty()
        }

        @Test
        fun `leaving the same state when deleted twice`() {
            val kept = message(first)
            val deleted = message(first)
            write(kept, deleted)

            store.delete(listOf(deleted.id))
            store.delete(listOf(deleted.id))

            all(first) shouldContainExactly listOf(kept)
        }
    }

    @Nested
    @DisplayName("read a page of a shard")
    inner class Page {

        @Test
        fun `ordered by the receive time, the version, and the UUID`() {
            val ordered = listOf(
                message(first, seconds = 9, nanos = 999_999_999, version = 7, uuid = "z"),
                message(first, seconds = 10, uuid = "b"),
                message(first, seconds = 10, uuid = "c"),
                message(first, seconds = 10, version = 1, uuid = "a"),
                message(first, seconds = 10, nanos = 5, version = -1, uuid = "y"),
                message(first, seconds = 11, uuid = "0"),
            )
            store.write(ordered.shuffled(Random(1)))

            all(first) shouldContainExactly ordered
        }

        @Test
        fun `ordering UUIDs by Unicode code points`() {
            // In UTF-16, the surrogate pair of U+1F600 sorts before U+FFFF; in UTF-8 and by
            // code points, it sorts after.
            val ordered = listOf(
                message(first, uuid = "￿"),
                message(first, uuid = "😀"),
            )
            store.write(ordered.reversed())

            all(first) shouldContainExactly ordered
        }

        @Test
        fun `received strictly after the given time`() {
            val before = message(first, seconds = 10, nanos = 499)
            val exact = message(first, seconds = 10, nanos = 500)
            val exactNewer = message(first, seconds = 10, nanos = 500, version = 5)
            val after = message(first, seconds = 10, nanos = 501)
            val later = message(first, seconds = 11)
            write(later, exactNewer, after, exact, before)

            store.page(first, time(10, 500), 10) shouldContainExactly listOf(after, later)
            store.page(first, time(10, 499), 10) shouldContainExactly
                    listOf(exact, exactNewer, after, later)
            store.page(first, time(11), 10).shouldBeEmpty()
        }

        @Test
        fun `of at most the page size`() {
            val messages = (1..5).map { message(first, seconds = it.toLong()) }
            store.write(messages)

            store.page(first, null, 2) shouldContainExactly messages.take(2)
            store.page(first, time(2), 2) shouldContainExactly messages.subList(2, 4)
            store.page(first, time(4), 2) shouldContainExactly messages.takeLast(1)
        }

        @Test
        fun `of a shard without messages`() {
            write(message(first))

            store.page(second, null, 10).shouldBeEmpty()
            store.page(second, time(0), 10).shouldBeEmpty()
        }

        @Test
        fun `rejecting a page size that is not positive`() {
            write(message(first))

            shouldThrow<IllegalArgumentException> { store.page(first, null, 0) }
            shouldThrow<IllegalArgumentException> { store.page(first, time(0), -1) }
            shouldThrow<IllegalArgumentException> { store.page(second, null, 0) }
        }

        @Test
        fun `comparing timestamps and versions as plain numbers`() {
            val ordered = listOf(
                message(
                    first,
                    seconds = Long.MIN_VALUE,
                    nanos = Int.MIN_VALUE,
                    version = Int.MIN_VALUE,
                    uuid = "a"
                ),
                message(first, seconds = Long.MIN_VALUE, nanos = 0, version = 0, uuid = "b"),
                message(first, seconds = -1, nanos = -1, version = -1, uuid = "c"),
                message(first, seconds = -1, nanos = 0, version = 0, uuid = "d"),
                message(first, seconds = 0, nanos = 0, version = Int.MIN_VALUE, uuid = "e"),
                message(first, seconds = 0, nanos = 0, version = -1, uuid = "f"),
                message(first, seconds = 0, nanos = 0, version = 0, uuid = "g"),
                message(first, seconds = 0, nanos = 0, version = Int.MAX_VALUE, uuid = "h"),
                message(first, seconds = 0, nanos = Int.MAX_VALUE, version = 0, uuid = "i"),
                message(
                    first,
                    seconds = Long.MAX_VALUE,
                    nanos = Int.MAX_VALUE,
                    version = Int.MAX_VALUE,
                    uuid = "j"
                ),
            )
            store.write(ordered.shuffled(Random(2)))

            all(first) shouldContainExactly ordered
            store.page(first, time(Long.MIN_VALUE, Int.MIN_VALUE), 100) shouldContainExactly
                    ordered.drop(1)
            store.page(first, time(-1, -1), 100) shouldContainExactly ordered.drop(3)
            store.page(first, time(Long.MAX_VALUE, Int.MAX_VALUE), 100).shouldBeEmpty()
            store.newestToDeliver(first) shouldBe ordered.last()
        }

        @Test
        fun `of a shard with more messages than one Redis script returns at once`() {
            val messages = (1..LARGE_SHARD).map { message(first, seconds = it.toLong()) }
            store.write(messages.shuffled(Random(3)))

            store.page(first, null, LARGE_SHARD + 1) shouldContainExactly messages
            store.page(first, time(500), LARGE_SHARD) shouldHaveSize LARGE_SHARD - 500
        }
    }

    @Nested
    @DisplayName("find the newest message to deliver")
    inner class NewestToDeliver {

        @Test
        fun `among the messages of the shard`() {
            val older = message(first, seconds = 10)
            val newer = message(first, seconds = 20)
            val delivered = message(first, seconds = 30, status = DELIVERED)
            val catchingUp = message(first, seconds = 40, status = TO_CATCH_UP)
            val otherShard = message(second, seconds = 50)
            write(delivered, newer, catchingUp, older, otherShard)

            store.newestToDeliver(first) shouldBe newer

            write(newer.withStatus(DELIVERED))
            store.newestToDeliver(first) shouldBe older

            store.delete(listOf(older.id))
            store.newestToDeliver(first).shouldBeNull()
        }

        @Test
        fun `breaking a tie by the version and the UUID`() {
            val lower = message(first, seconds = 10, version = 1, uuid = "z")
            val higher = message(first, seconds = 10, version = 2, uuid = "a")
            val higherUuid = message(first, seconds = 10, version = 2, uuid = "b")
            write(higherUuid, lower, higher)

            store.newestToDeliver(first) shouldBe higherUuid
        }

        @Test
        fun `in a shard without messages`() {
            write(message(first))

            store.newestToDeliver(second).shouldBeNull()
        }
    }

    @Nested
    @DisplayName("count messages")
    inner class Count {

        @Test
        fun `of an empty store`() {
            store.count(first) shouldBe 0
            store.count(listOf(first, second)) shouldBe mapOf(first to 0, second to 0)
            store.counts().shouldBeEmpty()
        }

        @Test
        fun `after every kind of change`() {
            val inFirst = (1..3).map { message(first, seconds = it.toLong()) }
            val inSecond = message(second)
            store.write(inFirst + inSecond)

            store.count(first) shouldBe 3
            store.count(listOf(first, second, third)) shouldBe
                    mapOf(first to 3, second to 1, third to 0)
            store.counts() shouldBe mapOf(first to 3, second to 1)

            write(inFirst[0].withStatus(DELIVERED))
            store.count(first) shouldBe 3

            store.delete(listOf(inSecond.id, inFirst[1].id))
            store.count(first) shouldBe 2
            store.count(second) shouldBe 0
            store.counts() shouldBe mapOf(first to 2)

            store.delete(inFirst.map { it.id })
            store.count(listOf(first, second)) shouldBe mapOf(first to 0, second to 0)
            store.counts().shouldBeEmpty()
        }
    }

    @Nested
    @DisplayName("report a change of a shard")
    inner class Changes {

        private lateinit var changes: ChangeRecorder
        private lateinit var subscription: Subscription

        @BeforeEach
        fun subscribe() {
            changes = ChangeRecorder(changeTimeout, quietPeriod)
            subscription = store.subscribe(changes)
        }

        @Test
        fun `after a write`() {
            val message = message(first)
            write(message)
            changes.expect(first)

            write(message(first), message(second))
            changes.expect(first, second)

            write(message)
            changes.expect(first)
        }

        @Test
        fun `after a delete that removes a message`() {
            val inFirst = message(first)
            val inSecond = message(second)
            write(inFirst, inSecond)
            changes.expect(first, second)

            store.delete(listOf(inFirst.id, id(second), id(third)))
            changes.expect(first)
        }

        @Test
        fun `only if a delete removes a message`() {
            write(message(first))
            changes.expect(first)

            store.delete(listOf(id(first), id(second)))
            changes.expectNone()
        }

        @Test
        fun `only while subscribed`() {
            subscription.cancel()

            write(message(first))
            changes.expectNone()
        }
    }

    @Nested
    @DisplayName("handle concurrent changes")
    inner class Concurrency {

        /**
         * The shards changed by the writers.
         */
        private val shards = (0 until WRITERS).map { shard(it) }

        @Test
        fun `of several shards`() {
            val writers = changeConcurrently(readers = 0)

            checkHeld(writers)
        }

        @Test
        fun `while the shards are read`() {
            val writers = changeConcurrently(readers = 2)

            checkHeld(writers)
        }

        /**
         * Runs [ROUNDS] rounds of a writer per shard, each on a thread of its own, while
         * the given number of other threads read the shards.
         *
         * Each writer adds messages to its own shard and to the next one, so that every shard
         * is changed by two writers.
         *
         * If a thread fails, or doesn't finish in time, the other threads stop after their
         * current round or read. The method waits for them to stop, for at most
         * [CONCURRENCY_TIMEOUT], so that they don't change the data of the next test.
         *
         * @return The writers, after their rounds.
         */
        private fun changeConcurrently(readers: Int): List<InboxWriter> {
            val writers = shards.mapIndexed { number, own ->
                InboxWriter(
                    store,
                    number = number,
                    own = own,
                    next = shards[(number + 1) % shards.size]
                )
            }
            val start = CountDownLatch(1)
            val writing = AtomicBoolean(true)
            val pool = Executors.newFixedThreadPool(writers.size + readers)
            try {
                val reads = (1..readers).map {
                    pool.submit {
                        start.await()
                        do {
                            shards.forEach(::checkReads)
                        } while (writing.get())
                    }
                }
                val writes = writers.map { writer ->
                    pool.submit {
                        start.await()
                        repeat(ROUNDS) {
                            if (writing.get()) {
                                writer.round()
                            }
                        }
                    }
                }
                start.countDown()
                writes.forEach { it.get(CONCURRENCY_TIMEOUT.seconds, SECONDS) }
                writing.set(false)
                reads.forEach { it.get(CONCURRENCY_TIMEOUT.seconds, SECONDS) }
            } finally {
                writing.set(false)
                pool.shutdownNow()
                pool.awaitTermination(CONCURRENCY_TIMEOUT.seconds, SECONDS)
            }
            return writers
        }

        /**
         * Reads the shard as a client does, and checks that what is read is consistent,
         * whatever is changed meanwhile.
         */
        private fun checkReads(shard: ShardIndex) {
            checkPage(shard, store.page(shard, null, READ_PAGE_SIZE))

            // The writers receive their messages at whole seconds, so a message received
            // after `since` has more seconds.
            val since = time(RECEIVE_SECONDS / 2)
            val later = store.page(shard, since, READ_PAGE_SIZE)
            checkPage(shard, later)
            later.forEach { it.whenReceived.seconds shouldBeGreaterThan since.seconds }

            val newest = store.newestToDeliver(shard) ?: return
            newest.id.index shouldBe shard
            newest.status shouldBe TO_DELIVER
        }

        /**
         * Checks that the page holds messages of the shard, in the ascending order of their
         * order keys, and no more than [READ_PAGE_SIZE] of them.
         */
        private fun checkPage(shard: ShardIndex, page: List<InboxMessage>) {
            page.size shouldBeLessThanOrEqual READ_PAGE_SIZE
            page.forEach { it.id.index shouldBe shard }
            page.zipWithNext().forEach { (earlier, later) ->
                orderKeys.compare(earlier, later) shouldBeLessThan 0
            }
        }

        /**
         * Checks that the store holds exactly the messages that the writers left in it.
         */
        private fun checkHeld(writers: List<InboxWriter>) {
            val expected = writers.flatMap { it.messages }
                .groupBy { it.id.index }
                .mapValues { (_, messages) -> messages.sortedWith(orderKeys) }
            for (shard in shards) {
                val messages = expected[shard].orEmpty()
                all(shard) shouldContainExactly messages
                store.newestToDeliver(shard) shouldBe
                        messages.lastOrNull { it.status == TO_DELIVER }
            }
            store.counts() shouldBe expected.mapValues { (_, messages) -> messages.size }
        }
    }
}
