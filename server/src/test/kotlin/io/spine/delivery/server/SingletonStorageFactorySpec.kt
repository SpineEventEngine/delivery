/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.spine.delivery.server

import com.google.protobuf.Message
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.spine.delivery.server.given.StoragesTestEnv.newTestContext
import io.spine.delivery.server.given.StoragesTestEnv.specForTestEvent
import io.spine.server.ContextSpec
import io.spine.server.storage.RecordSpec
import io.spine.server.storage.RecordStorage
import io.spine.server.storage.StorageFactory
import io.spine.server.storage.StorageGroup
import io.spine.server.storage.memory.InMemoryStorageFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Verifies the caching behavior of [SingletonStorageFactory]: one storage per
 * `(context, spec, group)` key, including the concurrent creation the delivery
 * layer performs on worker threads.
 */
@DisplayName("`SingletonStorageFactory` should")
internal class SingletonStorageFactorySpec {

    @Test
    fun `return the same storage for the same context, spec, and group`() {
        val factory = SingletonStorageFactory(InMemoryStorageFactory.newInstance())
        val context = newTestContext()
        val group = StorageGroup("example.Journal")

        val first = factory.createRecordStorage(context, specForTestEvent(), group)
        val second = factory.createRecordStorage(context, specForTestEvent(), group)

        second shouldBeSameInstanceAs first
    }

    @Test
    fun `separate a grouped storage from the ungrouped one over the same record spec`() {
        val factory = SingletonStorageFactory(InMemoryStorageFactory.newInstance())
        val context = newTestContext()

        val ungrouped = factory.createRecordStorage(context, specForTestEvent(), null)
        val grouped = factory.createRecordStorage(
            context, specForTestEvent(), StorageGroup("example.Journal"))

        grouped shouldNotBeSameInstanceAs ungrouped
    }

    @Test
    fun `separate the storages of different groups over the same record spec`() {
        val factory = SingletonStorageFactory(InMemoryStorageFactory.newInstance())
        val context = newTestContext()

        val journal = factory.createRecordStorage(
            context, specForTestEvent(), StorageGroup("example.Journal"))
        val history = factory.createRecordStorage(
            context, specForTestEvent(), StorageGroup("example.History"))

        history shouldNotBeSameInstanceAs journal
    }

    @Test
    fun `create a storage exactly once under concurrent access`() {
        val counting = CountingFactory(InMemoryStorageFactory.newInstance())
        val factory = SingletonStorageFactory(counting)
        val context = newTestContext()
        val group = StorageGroup("example.Journal")
        val threads = 8
        val ready = CountDownLatch(threads)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(threads)
        try {
            val futures = (1..threads).map {
                executor.submit<RecordStorage<*, *>> {
                    ready.countDown()
                    start.await()
                    factory.createRecordStorage(context, specForTestEvent(), group)
                }
            }
            ready.await(10, SECONDS) shouldBe true
            start.countDown()
            val storages = futures.map { it.get(10, SECONDS) }

            counting.created.get() shouldBe 1
            storages.forEach { it shouldBeSameInstanceAs storages.first() }
        } finally {
            executor.shutdownNow()
        }
    }

    /**
     * A [StorageFactory] counting the storages it has created.
     */
    @Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
    private class CountingFactory(
        private val delegate: StorageFactory
    ) : StorageFactory by delegate {

        val created = AtomicInteger()

        override fun <I : Any, R : Message> createRecordStorage(
            context: ContextSpec,
            recordSpec: RecordSpec<I, R>,
            group: StorageGroup?
        ): RecordStorage<I, R> {
            created.incrementAndGet()
            return delegate.createRecordStorage(context, recordSpec, group)
        }
    }
}
