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
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.spine.delivery.storage.given.id
import io.spine.delivery.storage.given.message
import io.spine.delivery.storage.given.receivedAt
import io.spine.delivery.storage.given.shard
import io.spine.delivery.storage.given.time
import io.spine.delivery.storage.given.withStatus
import io.spine.server.delivery.InboxMessage
import io.spine.server.delivery.InboxMessageStatus.DELIVERED
import io.spine.server.delivery.InboxMessageStatus.TO_CATCH_UP
import io.spine.server.delivery.ShardIndex
import java.time.Duration
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
 * The contract of [InboxStore], which every backend passes.
 *
 * A subclass creates a new, empty store for each test.
 */
public abstract class InboxStoreContract {

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

    private lateinit var store: InboxStore

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

    private fun write(vararg messages: InboxMessage) = store.write(messages.asList())

    private fun all(shard: ShardIndex): List<InboxMessage> = store.page(shard, null, Int.MAX_VALUE)

    @Nested
    @DisplayName("find a message")
    public inner class Find {

        @Test
        public fun `by its ID`() {
            val message = message(first)
            write(message, message(first), message(second))

            store.find(message.id) shouldBe message
        }

        @Test
        public fun `with an ID that is absent`() {
            write(message(first))

            store.find(id(first)).shouldBeNull()
            store.find(id(third)).shouldBeNull()
        }

        @Test
        public fun `only in the shard of its ID`() {
            val message = message(first)
            write(message)

            store.find(id(second, message.id.uuid)).shouldBeNull()
        }
    }

    @Nested
    @DisplayName("write messages")
    public inner class Write {

        @Test
        public fun `replacing a message with the same ID`() {
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
        public fun `keeping the last of the messages with the same ID in a batch`() {
            val message = message(first, version = 1)
            val replacement = message.receivedAt(seconds = 5, version = 2)
            val other = message(second)
            store.write(listOf(message, other, replacement))

            store.find(message.id) shouldBe replacement
            store.count(first) shouldBe 1
            store.count(second) shouldBe 1
        }

        @Test
        public fun `of several shards in one batch`() {
            val messages = listOf(message(first), message(second), message(first))
            store.write(messages)

            store.counts() shouldBe mapOf(first to 2, second to 1)
            messages.forEach { store.find(it.id) shouldBe it }
        }

        @Test
        public fun `leaving the same state when written twice`() {
            val messages = listOf(message(first, seconds = 2), message(first, seconds = 1))
            store.write(messages)
            store.write(messages)

            all(first) shouldContainExactly messages.reversed()
            store.counts() shouldBe mapOf(first to 2)
        }

        @Test
        public fun `in a batch larger than one Redis chunk by count`() {
            val messages = (1..LARGE_SHARD).map { message(first, seconds = it.toLong()) }
            store.write(messages)

            store.count(first) shouldBe LARGE_SHARD
        }

        @Test
        public fun `in a batch larger than one Redis chunk by size`() {
            val messages = (1..4).map {
                message(first, seconds = it.toLong(), payloadSize = LARGE_PAYLOAD)
            }
            store.write(messages)

            all(first) shouldContainExactly messages
        }

        @Test
        public fun `close to the maximum size of an inbound message`() {
            val message = message(first, payloadSize = NEAR_MAX_PAYLOAD)
            write(message)

            store.find(message.id) shouldBe message
            all(first) shouldContainExactly listOf(message)
        }
    }

    @Nested
    @DisplayName("delete messages")
    public inner class Delete {

        @Test
        public fun `by their IDs`() {
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
        public fun `ignoring absent and repeated IDs`() {
            val message = message(first)
            write(message)

            store.delete(listOf(id(first), message.id, message.id, id(third)))

            store.count(first) shouldBe 0
            store.counts().shouldBeEmpty()
        }

        @Test
        public fun `leaving the same state when deleted twice`() {
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
    public inner class Page {

        @Test
        public fun `ordered by the receive time, the version, and the UUID`() {
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
        public fun `ordering UUIDs by Unicode code points`() {
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
        public fun `received strictly after the given time`() {
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
        public fun `of at most the page size`() {
            val messages = (1..5).map { message(first, seconds = it.toLong()) }
            store.write(messages)

            store.page(first, null, 2) shouldContainExactly messages.take(2)
            store.page(first, time(2), 2) shouldContainExactly messages.subList(2, 4)
            store.page(first, time(4), 2) shouldContainExactly messages.takeLast(1)
        }

        @Test
        public fun `of a shard without messages`() {
            write(message(first))

            store.page(second, null, 10).shouldBeEmpty()
            store.page(second, time(0), 10).shouldBeEmpty()
        }

        @Test
        public fun `rejecting a page size that is not positive`() {
            write(message(first))

            shouldThrow<IllegalArgumentException> { store.page(first, null, 0) }
            shouldThrow<IllegalArgumentException> { store.page(first, time(0), -1) }
            shouldThrow<IllegalArgumentException> { store.page(second, null, 0) }
        }

        @Test
        public fun `comparing timestamps and versions as plain numbers`() {
            val ordered = listOf(
                message(first, Long.MIN_VALUE, Int.MIN_VALUE, Int.MIN_VALUE, uuid = "a"),
                message(first, Long.MIN_VALUE, 0, 0, uuid = "b"),
                message(first, -1, -1, -1, uuid = "c"),
                message(first, -1, 0, 0, uuid = "d"),
                message(first, 0, 0, Int.MIN_VALUE, uuid = "e"),
                message(first, 0, 0, -1, uuid = "f"),
                message(first, 0, 0, 0, uuid = "g"),
                message(first, 0, 0, Int.MAX_VALUE, uuid = "h"),
                message(first, 0, Int.MAX_VALUE, 0, uuid = "i"),
                message(first, Long.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, uuid = "j"),
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
        public fun `of a shard with more messages than one Redis script returns at once`() {
            val messages = (1..LARGE_SHARD).map { message(first, seconds = it.toLong()) }
            store.write(messages.shuffled(Random(3)))

            store.page(first, null, LARGE_SHARD + 1) shouldContainExactly messages
            store.page(first, time(500), LARGE_SHARD) shouldHaveSize LARGE_SHARD - 500
        }
    }

    @Nested
    @DisplayName("find the newest message to deliver")
    public inner class NewestToDeliver {

        @Test
        public fun `among the messages of the shard`() {
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
        public fun `breaking a tie by the version and the UUID`() {
            val lower = message(first, seconds = 10, version = 1, uuid = "z")
            val higher = message(first, seconds = 10, version = 2, uuid = "a")
            val higherUuid = message(first, seconds = 10, version = 2, uuid = "b")
            write(higherUuid, lower, higher)

            store.newestToDeliver(first) shouldBe higherUuid
        }

        @Test
        public fun `in a shard without messages`() {
            write(message(first))

            store.newestToDeliver(second).shouldBeNull()
        }
    }

    @Nested
    @DisplayName("count messages")
    public inner class Count {

        @Test
        public fun `of an empty store`() {
            store.count(first) shouldBe 0
            store.count(listOf(first, second)) shouldBe mapOf(first to 0, second to 0)
            store.counts().shouldBeEmpty()
        }

        @Test
        public fun `after every kind of change`() {
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
    public inner class Changes {

        private lateinit var changes: ChangeRecorder
        private lateinit var subscription: Subscription

        @BeforeEach
        public fun subscribe() {
            changes = ChangeRecorder(changeTimeout, quietPeriod)
            subscription = store.subscribe(changes)
        }

        @Test
        public fun `after a write`() {
            val message = message(first)
            write(message)
            changes.expect(first)

            write(message(first), message(second))
            changes.expect(first, second)

            write(message)
            changes.expect(first)
        }

        @Test
        public fun `after a delete that removes a message`() {
            val inFirst = message(first)
            val inSecond = message(second)
            write(inFirst, inSecond)
            changes.expect(first, second)

            store.delete(listOf(inFirst.id, id(second), id(third)))
            changes.expect(first)
        }

        @Test
        public fun `only if a delete removes a message`() {
            write(message(first))
            changes.expect(first)

            store.delete(listOf(id(first), id(second)))
            changes.expectNone()
        }

        @Test
        public fun `only while subscribed`() {
            subscription.cancel()

            write(message(first))
            changes.expectNone()
        }
    }
}
