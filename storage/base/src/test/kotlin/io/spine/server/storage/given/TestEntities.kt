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
