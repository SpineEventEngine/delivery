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

package io.spine.delivery.storage.hazelcast

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.spine.delivery.storage.CasOutcome
import io.spine.delivery.storage.ChangeRecorder
import io.spine.delivery.storage.given.message
import io.spine.delivery.storage.given.session
import io.spine.delivery.storage.given.shard
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * How long a test waits for something that must happen in the cluster.
 */
private val CLUSTER_TIMEOUT: Duration = Duration.ofSeconds(30)

@DisplayName("Hazelcast stores of two members should")
internal class HazelcastClusterSpec {

    private lateinit var first: HazelcastStores
    private lateinit var second: HazelcastStores

    @BeforeEach
    fun startMembers() {
        val cluster = "delivery-test-${UUID.randomUUID()}"
        first = HazelcastStores.start(testConfig(cluster))
        second = HazelcastStores.start(testConfig(cluster))
        val deadline = System.nanoTime() + CLUSTER_TIMEOUT.toNanos()
        while (first.instance.cluster.members.size < 2) {
            check(System.nanoTime() < deadline) { "The members have not formed a cluster." }
            Thread.sleep(100)
        }
    }

    @AfterEach
    fun stopMembers() {
        listOf(first, second).forEach {
            if (it.instance.lifecycleService.isRunning) {
                it.close()
            }
        }
    }

    @Test
    fun `serve the messages written through either member`() {
        val messages = (0 until 30).map { message(shard(it), seconds = it.toLong()) }
        first.inbox.write(messages)

        messages.forEach { second.inbox.find(it.id) shouldBe it }
        second.inbox.counts() shouldBe messages.associate { it.id.index to 1 }
    }

    @Test
    fun `report a change made through one member to the subscribers of the other`() {
        val changes = ChangeRecorder(CLUSTER_TIMEOUT, Duration.ofMillis(300))
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
            val outcomes = results.map { it.get(CLUSTER_TIMEOUT.seconds, SECONDS) }

            for (i in shards.indices) {
                outcomes.count { it[i] == CasOutcome.Applied } shouldBe 1
            }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `keep all data after a member is terminated`() {
        val messages = (0 until 300).map { message(shard(it), seconds = it.toLong()) }
        first.inbox.write(messages)
        val sessions = (0 until 300).map { session(shard(it)) }
        sessions.forEach { first.sessions.compareAndSet(it.index, null, it) }

        first.instance.lifecycleService.terminate()

        messages.forEach { second.inbox.find(it.id) shouldBe it }
        second.inbox.counts().values.sum() shouldBe messages.size
        second.sessions.readAll() shouldHaveSize sessions.size
    }

    @Test
    fun `report missed changes when a member leaves`() {
        val missed = Semaphore(0)
        second.inbox.subscribeToMissedChanges { missed.release() }

        first.instance.lifecycleService.terminate()

        missed.tryAcquire(CLUSTER_TIMEOUT.seconds, SECONDS) shouldBe true
    }
}
