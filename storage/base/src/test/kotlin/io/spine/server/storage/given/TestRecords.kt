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

@file:JvmName("TestRecords")

package io.spine.server.storage.given

import io.spine.base.Identifier
import io.spine.base.Time.currentTime
import io.spine.core.Versions
import io.spine.protobuf.AnyPacker
import io.spine.server.entity.EntityRecord
import io.spine.server.entity.entityRecord
import io.spine.server.storage.RecordSpec
import io.spine.test.entity.Project
import io.spine.test.entity.ProjectId
import io.spine.test.entity.project
import io.spine.test.entity.projectId

/**
 * Creates the specification of a record storage over [Project] records.
 */
fun projectRecordSpec(): RecordSpec<ProjectId, Project> =
    RecordSpec(ProjectId::class.java, Project::class.java) { it.id }

/**
 * Generates a new unique [ProjectId].
 */
fun newProjectId(): ProjectId = projectId {
    id = Identifier.newUuid()
}

/**
 * Creates a new [Project] with the given ID.
 */
fun newProject(id: ProjectId): Project = project {
    this.id = id
    name = "Project ${id.id}"
}

/**
 * Creates a new [EntityRecord] over a [Project] with the given ID,
 * at the given version.
 */
fun newEntityRecord(id: ProjectId, version: Int): EntityRecord = entityRecord {
    entityId = Identifier.pack(id)
    state = AnyPacker.pack(newProject(id))
    this.version = Versions.newVersion(version, currentTime())
}
