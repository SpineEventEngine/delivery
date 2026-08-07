/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.server.storage.hazelcast

import io.kotest.matchers.shouldBe
import io.spine.server.ContextSpec
import io.spine.server.storage.StorageGroup
import io.spine.server.storage.given.ProjectEntity
import io.spine.server.storage.given.ProjectLogEntity
import io.spine.server.storage.given.newEntityRecord
import io.spine.server.storage.given.newProject
import io.spine.server.storage.given.newProjectId
import io.spine.server.storage.given.projectRecordSpec
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Verifies that [HazelcastStorageFactory] allocates a physically distinct map
 * per [StorageGroup], so that the storages sharing a record specification —
 * e.g. the latest states of an entity and its state history — never conflate.
 */
@DisplayName("`HazelcastStorageFactory` should")
internal class HazelcastGroupedStorageSpec {

    private lateinit var factory: HazelcastStorageFactory

    @BeforeEach
    fun createFactory() {
        factory = HazelcastStorageFactory.newInstance()
    }

    @AfterEach
    fun closeFactory() {
        factory.close()
    }

    @Test
    fun `separate a grouped storage from the ungrouped one over the same record spec`() {
        val ungrouped = factory.createRecordStorage(context, projectRecordSpec(), null)
        val grouped =
            factory.createRecordStorage(context, projectRecordSpec(), StorageGroup("example.Journal"))
        val id = newProjectId()
        ungrouped.write(id, newProject(id))

        grouped.read(id).isPresent shouldBe false
        ungrouped.read(id).isPresent shouldBe true
    }

    @Test
    fun `separate the storages of different groups over the same record spec`() {
        val journal =
            factory.createRecordStorage(context, projectRecordSpec(), StorageGroup("example.Journal"))
        val history =
            factory.createRecordStorage(context, projectRecordSpec(), StorageGroup("example.History"))
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

    @Test
    fun `separate the storages of entity types sharing the ID type`() {
        val projects = factory.createEntityRecordStorage(context, ProjectEntity::class.java)
        val logs = factory.createEntityRecordStorage(context, ProjectLogEntity::class.java)
        val id = newProjectId()
        projects.write(id, newEntityRecord(id, version = 1))

        logs.read(id).isPresent shouldBe false
        projects.read(id).isPresent shouldBe true
    }

    companion object {

        private const val BATCH = 10

        private val context = ContextSpec.singleTenant("HazelcastGroupedStorageSpec")
    }
}
