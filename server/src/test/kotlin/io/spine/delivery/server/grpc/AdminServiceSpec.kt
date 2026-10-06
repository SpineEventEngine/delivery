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

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.spine.base.Identifier.newUuid
import io.spine.delivery.admin.grpc.ShardInfoList
import io.spine.delivery.given.TestInboxMessages.copyWithStatus
import io.spine.delivery.given.TestInboxMessages.toDeliver
import io.spine.delivery.server.ExtendedInboxStorage
import io.spine.delivery.server.ReportingStorageFactory
import io.spine.delivery.server.SingletonStorageFactory
import io.spine.delivery.server.WithApp
import io.spine.delivery.server.grpc.given.AdminServiceTestEnv.copyWithNewShard
import io.spine.delivery.server.grpc.given.AdminServiceTestEnv.removeMessages
import io.spine.delivery.server.grpc.given.AdminServiceTestEnv.request
import io.spine.delivery.server.grpc.given.AdminServiceTestEnv.writeMessage
import io.spine.delivery.server.grpc.given.AdminServiceTestEnv.writeMessages
import io.spine.grpc.StreamObservers.memoizingObserver
import io.spine.server.delivery.DeliveryStrategy.newIndex
import io.spine.server.delivery.InboxMessage
import io.spine.server.delivery.InboxMessageStatus.DELIVERED
import io.spine.server.delivery.ShardIndex
import io.spine.server.storage.memory.InMemoryStorageFactory
import io.spine.test.delivery.Something
import io.spine.type.TypeUrl
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Verifies that the number of messages [AdminService] reports for a shard matches
 * the messages actually stored, even after the storage operations that leave the stored
 * messages as they are: a repeated removal, or a rewrite of a stored message.
 *
 * Complements the Java [AdminServiceTest], which covers the notifications about
 * the ordinary writes and removals.
 *
 * @see io.spine.delivery.server.grpc.AdminServiceTest
 */
@DisplayName("`AdminService` should")
internal class AdminServiceSpec : WithApp() {

    private val shard: ShardIndex = newIndex(1, 5)

    @Test
    fun `report the real count after the same messages are removed twice`() {
        val first = messageIn(shard)
        val second = messageIn(shard)
        syncInboxService().writeMany(writeMessages(shard, first, second))

        syncInboxService().removeMany(removeMessages(shard, first, second))
        syncInboxService().removeMany(removeMessages(shard, first, second))

        messagesPerShard() shouldContainExactly listOf(shard to 0)
    }

    @Test
    fun `report the real count after a removal lists the same message twice`() {
        val removed = messageIn(shard)
        val kept = messageIn(shard)
        syncInboxService().writeMany(writeMessages(shard, removed, kept))

        syncInboxService().removeMany(removeMessages(shard, removed, removed))

        messagesPerShard() shouldContainExactly listOf(shard to 1)
    }

    @Test
    fun `report the real count after the same message is written twice`() {
        val message = messageIn(shard)

        syncInboxService().writeOne(writeMessage(message))
        syncInboxService().writeOne(writeMessage(message))

        messagesPerShard() shouldContainExactly listOf(shard to 1)
    }

    @Test
    fun `report the real count after stored messages are rewritten in a batch`() {
        val first = messageIn(shard)
        val second = messageIn(shard)
        syncInboxService().writeMany(writeMessages(shard, first, second))

        val (firstDelivered, secondDelivered) =
            listOf(first, second).map { copyWithStatus(it, DELIVERED) }
        syncInboxService().writeMany(writeMessages(shard, firstDelivered, secondDelivered))

        messagesPerShard() shouldContainExactly listOf(shard to 2)
    }

    /**
     * Constructs the service directly over a storage that already holds messages,
     * bypassing the app started by [WithApp], whose storage is empty at that point.
     */
    @Test
    fun `count the messages stored before the service is created, once`() {
        val factory =
            ReportingStorageFactory(SingletonStorageFactory(InMemoryStorageFactory.newInstance()))
        val storage = ExtendedInboxStorage(factory, false)
        val stored = messageIn(shard)
        val another = messageIn(shard)
        storage.writeBatch(listOf(stored, another))

        val service = AdminService(factory)
        storage.write(stored.id, copyWithStatus(stored, DELIVERED))

        val observer = memoizingObserver<ShardInfoList>()
        service.getShardInfo(request(), observer)

        observer.error.shouldBeNull()
        observer.isCompleted shouldBe true
        observer.firstResponse().messagesPerShard() shouldContainExactly listOf(shard to 2)
    }

    /**
     * Fetches the shard information from the running app and returns the number
     * of messages reported for each shard.
     */
    private fun messagesPerShard(): List<Pair<ShardIndex, Int>> =
        syncAdminService()
            .getShardInfo(request())
            .messagesPerShard()

    private fun ShardInfoList.messagesPerShard(): List<Pair<ShardIndex, Int>> =
        shardsList.map { it.index to it.messages }

    private fun messageIn(index: ShardIndex): InboxMessage =
        copyWithNewShard(toDeliver(newUuid(), TypeUrl.of(Something::class.java)), index)
}
