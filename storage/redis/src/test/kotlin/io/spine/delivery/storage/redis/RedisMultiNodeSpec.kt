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

package io.spine.delivery.storage.redis

import io.kotest.matchers.shouldBe
import io.spine.delivery.storage.CasOutcome
import io.spine.delivery.storage.ChangeRecorder
import io.spine.delivery.storage.given.message
import io.spine.delivery.storage.given.session
import io.spine.delivery.storage.given.shard
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * How long a test waits for something that must happen.
 */
private val TIMEOUT: Duration = Duration.ofSeconds(30)

@DisplayName("Redis stores of two nodes should")
@RequiresDocker
internal class RedisMultiNodeSpec {

    private lateinit var first: RedisStores
    private lateinit var second: RedisStores

    @BeforeEach
    fun connect() {
        redis.clear()
        first = RedisStores.start(redis.config)
        second = RedisStores.start(redis.config)
    }

    @AfterEach
    fun disconnect() {
        first.close()
        second.close()
    }

    @Test
    fun `serve the messages written through either node`() {
        val messages = (0 until 30).map { message(shard(it), seconds = it.toLong()) }
        first.inbox.write(messages)

        messages.forEach { second.inbox.find(it.id) shouldBe it }
        second.inbox.counts() shouldBe messages.associate { it.id.index to 1 }
    }

    @Test
    fun `report a change made through one node to the subscribers of the other`() {
        val changes = ChangeRecorder(TIMEOUT, Duration.ofMillis(300))
        second.inbox.subscribe(changes)
        second.sessions.subscribe(changes)

        first.inbox.write(listOf(message(shard(1))))
        changes.expect(shard(1))
        first.sessions.compareAndSet(shard(2), null, session(shard(2)))
        changes.expect(shard(2))
    }

    @Test
    fun `let exactly one of concurrent creations of a session win`() {
        val shards = (0 until 50).map { shard(it) }
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = listOf(first, second).map { stores ->
                pool.submit(Callable {
                    start.await()
                    shards.map { stores.sessions.compareAndSet(it, null, session(it)) }
                })
            }
            start.countDown()
            val outcomes = results.map { it.get(TIMEOUT.seconds, SECONDS) }

            for (i in shards.indices) {
                outcomes.count { it[i] == CasOutcome.Applied } shouldBe 1
            }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `report missed changes when the subscription is established again`() {
        val inboxMissed = Semaphore(0)
        val sessionsMissed = Semaphore(0)
        second.inbox.subscribeToMissedChanges { inboxMissed.release() }
        second.sessions.subscribeToMissedChanges { sessionsMissed.release() }

        redis.dropSubscriptions()

        inboxMissed.tryAcquire(TIMEOUT.seconds, SECONDS) shouldBe true
        sessionsMissed.tryAcquire(TIMEOUT.seconds, SECONDS) shouldBe true
        val changes = ChangeRecorder(TIMEOUT, Duration.ofMillis(300))
        second.inbox.subscribe(changes)
        first.inbox.write(listOf(message(shard(3))))
        changes.expect(shard(3))
    }

    companion object {

        private val redis by lazy { TestRedis() }

        @JvmStatic
        @AfterAll
        fun stopRedis() {
            redis.close()
        }
    }
}
