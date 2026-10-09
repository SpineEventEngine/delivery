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
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.spine.delivery.storage.given.message
import io.spine.delivery.storage.given.shard
import io.spine.delivery.storage.given.time
import io.spine.delivery.storage.given.withStatus
import io.spine.server.delivery.InboxMessage
import io.spine.server.delivery.InboxMessageStatus.DELIVERED
import io.spine.server.delivery.InboxMessageStatus.TO_DELIVER
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`ShardInbox` should")
internal class ShardInboxSpec {

    private val shard = shard(1)
    /**
     * The inbox under test, holding the messages as they are.
     */
    private val inbox = ShardInbox(shard, TestForm)

    @Test
    fun `hold each UUID once`() {
        val message = message(shard, seconds = 1)
        inbox.put(message)
        inbox.put(message.withStatus(DELIVERED))

        inbox.size shouldBe 1
        inbox.page(null, 10) shouldContainExactly listOf(message.withStatus(DELIVERED))
        inbox.remove(message.id.uuid) shouldBe true
        inbox.remove(message.id.uuid) shouldBe false
        inbox.size shouldBe 0
        inbox.page(null, 10).shouldBeEmpty()
    }

    @Test
    fun `start a page after every message received at exactly the given time`() {
        val messages = listOf(
            message(shard, seconds = 5, version = -1),
            message(shard, seconds = 5),
            message(shard, seconds = 5, version = 1),
            message(shard, seconds = 5, nanos = 1)
        )
        messages.forEach(inbox::put)

        inbox.page(time(5), 10) shouldContainExactly messages.takeLast(1)
        inbox.page(time(4, 999_999_999), 10) shouldContainExactly messages
    }

    @Test
    fun `drop the map of messages to deliver when there are none`() {
        val message = message(shard, status = TO_DELIVER)
        inbox.put(message)
        inbox.newestToDeliver() shouldBe message

        inbox.put(message.withStatus(DELIVERED))
        inbox.newestToDeliver().shouldBeNull()
        inbox.put(message)
        inbox.newestToDeliver() shouldBe message
    }

    @Test
    fun `list its messages`() {
        val messages = (1..3).map { message(shard, seconds = it.toLong()) }
        messages.forEach(inbox::put)

        inbox.messages shouldContainExactlyInAnyOrder messages
    }

    @Test
    fun `reject a page size that is not positive`() {
        shouldThrow<IllegalArgumentException> { inbox.page(null, 0) }
    }

    @Test
    fun `compare strings by code points`() {
        compareByCodePoints("a", "b") shouldBe -1
        compareByCodePoints("b", "a") shouldBe 1
        compareByCodePoints("a", "a") shouldBe 0
        compareByCodePoints("a", "ab") shouldBe -1
        compareByCodePoints("￿", "😀") shouldBe -1
    }

    /**
     * Holds an `InboxMessage` as it is.
     */
    private object TestForm : MessageForm<InboxMessage>() {

        override fun uuid(message: InboxMessage): String = message.id.uuid

        override fun seconds(message: InboxMessage): Long = message.whenReceived.seconds

        override fun nanos(message: InboxMessage): Int = message.whenReceived.nanos

        override fun version(message: InboxMessage): Int = message.version

        override fun isToDeliver(message: InboxMessage): Boolean = message.status == TO_DELIVER
    }
}
