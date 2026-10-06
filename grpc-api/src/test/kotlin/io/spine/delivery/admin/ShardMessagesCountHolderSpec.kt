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

package io.spine.delivery.admin

import com.google.common.testing.NullPointerTester
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.spine.server.delivery.DeliveryStrategy.newIndex
import io.spine.server.delivery.InboxMessageId
import io.spine.server.delivery.InboxMessageMixin.generateIdWith
import io.spine.server.delivery.ShardIndex
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The number of messages the concurrency tests write or remove in total.
 */
private const val MESSAGES = 1_000

/**
 * The number of threads the concurrency tests run, each updating its own slice of messages.
 */
private const val THREADS = 8

/**
 * How long the concurrency tests wait for the threads to get ready or to finish.
 */
private const val TIMEOUT_SECONDS = 10L

/**
 * Verifies that [ShardMessagesCountHolder] derives the number of messages in a shard
 * from the identifiers it knows, so that recording the same write or removal twice
 * leaves the count unchanged.
 */
@DisplayName("`ShardMessagesCountHolder` should")
internal class ShardMessagesCountHolderSpec {

    private val shard: ShardIndex = newIndex(1, 5)
    private val anotherShard: ShardIndex = newIndex(2, 5)
    private val holder = ShardMessagesCountHolder()

    @Test
    fun `reject null arguments`() {
        NullPointerTester().testAllPublicInstanceMethods(holder)
    }

    @Test
    fun `know about no messages initially`() {
        holder.toMutableMap().shouldBeEmpty()
    }

    @Test
    fun `count written messages`() {
        holder.messageWritten(messageIn(shard)) shouldBe 1
        holder.messageWritten(messageIn(shard)) shouldBe 2
    }

    @Test
    fun `not count a message written twice`() {
        val id = messageIn(shard)
        holder.messageWritten(id)

        holder.messageWritten(id) shouldBe 1
        holder.toMutableMap() shouldContainExactly mapOf(shard to 1)
    }

    @Test
    fun `not count a removed message`() {
        val id = messageIn(shard)
        holder.messageWritten(id)

        holder.messageRemoved(id) shouldBe 0
        holder.toMutableMap() shouldContainExactly mapOf(shard to 0)
    }

    @Test
    fun `keep counting the messages of a drained shard`() {
        val drained = List(MESSAGES) { messageIn(shard) }
        drained.forEach { holder.messageWritten(it) }
        drained.forEach { holder.messageRemoved(it) }

        holder.messageWritten(messageIn(shard)) shouldBe 1
        holder.messageRemoved(drained.first()) shouldBe 1
        holder.toMutableMap() shouldContainExactly mapOf(shard to 1)
    }

    @Test
    fun `ignore a repeated removal of the same message`() {
        val removed = messageIn(shard)
        val kept = messageIn(shard)
        holder.messageWritten(removed)
        holder.messageWritten(kept)
        holder.messageRemoved(removed)

        holder.messageRemoved(removed) shouldBe 1
        holder.toMutableMap() shouldContainExactly mapOf(shard to 1)
    }

    @Test
    fun `ignore a removal of an unknown message`() {
        holder.messageRemoved(messageIn(shard)) shouldBe 0
        holder.toMutableMap().shouldBeEmpty()
    }

    @Test
    fun `count messages of each shard separately`() {
        holder.messageWritten(messageIn(shard))
        holder.messageWritten(messageIn(anotherShard))
        holder.messageWritten(messageIn(anotherShard))

        holder.toMutableMap() shouldContainExactly mapOf(shard to 1, anotherShard to 2)
    }

    @Test
    fun `return a copy of the counts`() {
        holder.messageWritten(messageIn(shard))
        val snapshot: MutableMap<ShardIndex, Int> = holder.toMutableMap()

        snapshot[shard] = 42
        snapshot[anotherShard] = 1

        holder.toMutableMap() shouldContainExactly mapOf(shard to 1)
    }

    @Test
    fun `return the exact count to each of concurrent writers`() {
        val ids = List(MESSAGES) { messageIn(shard) }
        val writers = ids.chunked(MESSAGES / THREADS).map { slice ->
            { slice.map { holder.messageWritten(it) } }
        }

        val counts = runConcurrently(writers).flatten()

        counts.sorted() shouldBe (1..MESSAGES).toList()
        holder.toMutableMap() shouldContainExactly mapOf(shard to MESSAGES)
    }

    @Test
    fun `count messages while others are written and removed concurrently`() {
        val removed = List(MESSAGES / 2) { messageIn(shard) }
        val kept = List(MESSAGES / 2) { messageIn(shard) }
        removed.forEach { holder.messageWritten(it) }
        val sliceSize = MESSAGES / THREADS
        val writers = kept.chunked(sliceSize).map { slice ->
            { slice.forEach { holder.messageWritten(it) } }
        }
        val removers = removed.chunked(sliceSize).map { slice ->
            { slice.forEach { holder.messageRemoved(it) } }
        }

        runConcurrently(writers + removers)

        holder.toMutableMap() shouldContainExactly mapOf(shard to kept.size)
    }

    private fun messageIn(index: ShardIndex): InboxMessageId = generateIdWith(index)

    /**
     * Runs each of the given [tasks] on a thread of its own, releasing them all at once,
     * and returns their results in the order of the tasks.
     */
    private fun <T> runConcurrently(tasks: List<() -> T>): List<T> {
        val executor = Executors.newFixedThreadPool(tasks.size)
        try {
            val ready = CountDownLatch(tasks.size)
            val start = CountDownLatch(1)
            val futures = tasks.map { task ->
                executor.submit<T> {
                    ready.countDown()
                    start.await()
                    task()
                }
            }
            ready.await(TIMEOUT_SECONDS, SECONDS) shouldBe true
            start.countDown()
            return futures.map { it.get(TIMEOUT_SECONDS, SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }
}
