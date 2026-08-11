/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.server.storage.redis

import io.kotest.matchers.shouldBe
import io.spine.server.ContextSpec
import io.spine.server.storage.StorageGroup
import io.spine.server.storage.given.ProjectEntity
import io.spine.server.storage.given.newEntityRecord
import io.spine.server.storage.given.newProject
import io.spine.server.storage.given.newProjectId
import io.spine.server.storage.given.projectRecordSpec
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

/**
 * Verifies that [RedisStorageFactory] allocates a physically distinct map
 * per [StorageGroup], so that the storages sharing a record specification —
 * e.g. the latest states of an entity and its state history — never conflate.
 */
@DisplayName("`RedisStorageFactory` should")
@RequiresDocker
internal class RedisGroupedStorageSpec {

    private val redis: GenericContainer<*> =
        GenericContainer(DockerImageName.parse("redis:6-alpine"))
            .withExposedPorts(REDIS_PORT)

    private lateinit var factory: RedisStorageFactory

    @BeforeEach
    fun startRedis() {
        redis.start()
        System.setProperty("REDIS_HOST", redis.host)
        System.setProperty("REDIS_PORT", redis.firstMappedPort.toString())
        factory = RedisStorageFactory.newInstance()
    }

    @AfterEach
    fun stopRedis() {
        if (::factory.isInitialized) {
            factory.close()
        }
        redis.stop()
    }

    @Test
    fun `separate a grouped storage from the ungrouped one over the same record spec`() {
        val ungrouped = factory.createRecordStorage(context, projectRecordSpec(), null)
        val grouped = factory.createRecordStorage(
            context, projectRecordSpec(), StorageGroup("example.Journal")
        )
        val id = newProjectId()
        ungrouped.write(id, newProject(id))

        grouped.read(id).isPresent shouldBe false
        ungrouped.read(id).isPresent shouldBe true
    }

    @Test
    fun `separate the storages of different groups over the same record spec`() {
        val journal = factory.createRecordStorage(
            context, projectRecordSpec(), StorageGroup("example.Journal")
        )
        val history = factory.createRecordStorage(
            context, projectRecordSpec(), StorageGroup("example.History")
        )
        val id = newProjectId()
        journal.write(id, newProject(id))

        history.read(id).isPresent shouldBe false
        journal.read(id).isPresent shouldBe true
    }

    @Test
    fun `separate the latest state of an entity from its state history`() {
        val latestStates = factory.createEntityRecordStorage(context, ProjectEntity::class.java)
        val history = factory.createEntityStateHistoryStorage(context, ProjectEntity::class.java)
        val id = newProjectId()
        val latest = newEntityRecord(id, version = 1)
        latestStates.write(id, latest)

        history.historyBackward(id, BATCH).hasNext() shouldBe false

        history.write(newEntityRecord(id, version = 2))

        latestStates.read(id).get() shouldBe latest
    }

    companion object {

        private const val BATCH = 10

        private const val REDIS_PORT = 6379

        private val context = ContextSpec.singleTenant("RedisGroupedStorageSpec")
    }
}
