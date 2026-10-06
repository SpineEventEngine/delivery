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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

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
    fun `count messages written and removed concurrently`() {
        val ids = List(1_000) { messageIn(shard) }
        val removed = ids.take(ids.size / 2)
        val executor = Executors.newFixedThreadPool(8)
        try {
            ids.map { id -> executor.submit<Int> { holder.messageWritten(id) } }
                .forEach { it.get(10, SECONDS) }
            holder.toMutableMap() shouldContainExactly mapOf(shard to ids.size)

            removed.map { id -> executor.submit<Int> { holder.messageRemoved(id) } }
                .forEach { it.get(10, SECONDS) }
            holder.toMutableMap() shouldContainExactly mapOf(shard to ids.size - removed.size)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun messageIn(index: ShardIndex): InboxMessageId = generateIdWith(index)
}
