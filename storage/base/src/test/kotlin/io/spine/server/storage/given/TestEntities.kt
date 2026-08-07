/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.server.storage.given

import io.spine.server.entity.AbstractEntity
import io.spine.test.entity.Project
import io.spine.test.entity.ProjectId
import io.spine.test.entity.ProjectLog

/**
 * An entity over the [Project] state.
 *
 * Used by the vendor storage tests exercising the entity-level storages
 * produced by a `StorageFactory`.
 */
class ProjectEntity(id: ProjectId) : AbstractEntity<ProjectId, Project>(id)

/**
 * An entity over the [ProjectLog] state.
 *
 * Shares the ID type with [ProjectEntity], allowing the tests to verify that
 * the storages of entity types sharing the ID type stay physically separate.
 */
class ProjectLogEntity(id: ProjectId) : AbstractEntity<ProjectId, ProjectLog>(id)
