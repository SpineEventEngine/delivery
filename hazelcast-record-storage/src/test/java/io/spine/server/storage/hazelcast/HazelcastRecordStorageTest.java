/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.server.storage.hazelcast;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.truth.Truth;
import io.spine.base.Identifier;
import io.spine.query.RecordColumn;
import io.spine.query.RecordQuery;
import io.spine.server.ContextSpec;
import io.spine.server.storage.MessageRecordSpec;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.RecordWithColumns;
import io.spine.test.entity.Project;
import io.spine.test.entity.ProjectId;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.test.entity.Project.Column.name;

// TODO:2023-06-15:nick.dolhii: Move this test class to a general module.
@DisplayName("`HazelcastRecordStorage` should")
final class HazelcastRecordStorageTest {

    private static final RecordSpec<ProjectId, Project, ?> recordSpec = new MessageRecordSpec<>(
            ProjectId.class, Project.class, Project::getId,
            ImmutableList.of(RecordColumn.create("name", name().type(), Project::getName))
    );
    private static final ContextSpec context = ContextSpec.singleTenant("RedisRecordStorageTest");

    @Nested
    @DisplayName("not throw exceptions when the storage is empty")
    class EmptyStorage {

        private @MonotonicNonNull HazelcastRecordStorage<ProjectId, Project> storage;

        private @MonotonicNonNull HazelcastStorageFactory factory;

        @BeforeEach
        void startRedis() {
            factory = HazelcastStorageFactory.newInstance();
            storage = factory.createRecordStorage(context, recordSpec);
        }

        @AfterEach
        void stopRedis() {
            factory.close();
        }

        @Test
        @DisplayName("when calling `index()`")
        void index() {
            assertThat(Iterators.size(storage.index()))
                    .isEqualTo(0);
        }

        @Test
        @DisplayName("when calling `index(RecordQuery query)`")
        void indexQuery() {
            ProjectId someId = ProjectId.newBuilder()
                    .setId(Identifier.newUuid())
                    .vBuild();
            RecordQuery<ProjectId, Project> query =
                    Project.query()
                           .id()
                           .is(someId)
                           .build()
                           .toRecordQuery();
            assertThat(Iterators.size(storage.index(query)))
                    .isEqualTo(0);
        }

        @Test
        @DisplayName("when calling `readAllRecords`")
        void readAllRecords() {
            ProjectId someId = ProjectId.newBuilder()
                    .setId(Identifier.newUuid())
                    .vBuild();
            RecordQuery<ProjectId, Project> query =
                    Project.query()
                           .id()
                           .is(someId)
                           .build()
                           .toRecordQuery();
            assertThat(Iterators.size(storage.readAllRecords(query)))
                    .isEqualTo(0);
        }

        @Test
        @DisplayName("when calling `deleteRecord`")
        void deleteRecord() {
            ProjectId someId = ProjectId.newBuilder()
                    .setId(Identifier.newUuid())
                    .vBuild();
            Truth.assertThat(storage.deleteRecord(someId))
                 .isFalse();
        }

        @Test
        @DisplayName("when calling `write`")
        void write() {
            ProjectId someId = ProjectId.newBuilder()
                    .setId(Identifier.newUuid())
                    .vBuild();
            Project someRecord = Project.newBuilder()
                    .setId(someId)
                    .buildPartial();
            storage.write(someId, someRecord);
        }

        @Test
        @DisplayName("when calling `writeAllRecords`")
        void writeAll() {
            ProjectId someId = ProjectId.newBuilder()
                    .setId(Identifier.newUuid())
                    .vBuild();
            Project someRecord = Project.newBuilder()
                    .setId(someId)
                    .buildPartial();
            storage.writeAllRecords(ImmutableList.of(RecordWithColumns.of(someId, someRecord)));
        }
    }

    @Nested
    @DisplayName("not throw exceptions when the working with a pre-filled storage and")
    class PreFilledStorage {

        private final ProjectId existingProjectId = ProjectId.newBuilder()
                .setId(Identifier.newUuid())
                .vBuild();
        private final Project existingProject = Project.newBuilder()
                .setId(existingProjectId)
                .setName(PreFilledStorage.class.getName())
                .buildPartial();

        private @MonotonicNonNull HazelcastRecordStorage<ProjectId, Project> storage;

        private @MonotonicNonNull HazelcastStorageFactory factory;

        @BeforeEach
        void startRedis() {
            factory = HazelcastStorageFactory.newInstance();
            storage = factory.createRecordStorage(context, recordSpec);
            storage.write(existingProjectId, existingProject);
        }

        @AfterEach
        void stopRedis() {
            factory.close();
        }

        @Test
        @DisplayName("return the record ID")
        void index() {
            assertThat(Iterators.size(storage.index()))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("not return record with non-matching query")
        void indexQuery() {
            ProjectId someId = ProjectId.newBuilder()
                    .setId(Identifier.newUuid())
                    .vBuild();
            RecordQuery<ProjectId, Project> query =
                    Project.query()
                           .id()
                           .is(someId)
                           .build()
                           .toRecordQuery();
            assertThat(Iterators.size(storage.index(query)))
                    .isEqualTo(0);
        }

        @Test
        @DisplayName("return record with matching ID query")
        void idQuery() {
            RecordQuery<ProjectId, Project> query =
                    Project.query()
                           .id()
                           .is(existingProjectId)
                           .build()
                           .toRecordQuery();
            assertThat(Iterators.size(storage.index(query)))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("return record with matching column query")
        void columnQuery() {
            RecordQuery<ProjectId, Project> query =
                    Project.query()
                           .name()
                           .is(existingProject.getName())
                           .build()
                           .toRecordQuery();
            assertThat(Iterators.size(storage.index(query)))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("read existing records")
        void readAllRecords() {
            ProjectId someId = ProjectId.newBuilder()
                    .setId(Identifier.newUuid())
                    .vBuild();
            RecordQuery<ProjectId, Project> query =
                    Project.query()
                           .id()
                           .in(someId, existingProjectId)
                           .build()
                           .toRecordQuery();
            assertThat(Iterators.size(storage.readAllRecords(query)))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("delete existing record")
        void deleteRecord() {
            Truth.assertThat(storage.deleteRecord(existingProjectId))
                 .isTrue();
        }

        @Test
        @DisplayName("overwrite existing record")
        void write() {
            ProjectId someId = ProjectId.newBuilder()
                    .setId(Identifier.newUuid())
                    .vBuild();
            Project someRecord = Project.newBuilder()
                    .setId(someId)
                    .buildPartial();
            storage.write(existingProjectId, someRecord);
            Project overwritten = storage.read(existingProjectId)
                                         .orElseThrow();
            assertThat(overwritten)
                    .isEqualTo(someRecord);
        }

        @Test
        @DisplayName("overwrite existing records while writing in batch")
        void writeAll() {
            ProjectId someId = ProjectId.newBuilder()
                    .setId(Identifier.newUuid())
                    .vBuild();
            Project someRecord = Project.newBuilder()
                    .setId(someId)
                    .buildPartial();
            storage.writeAllRecords(ImmutableList.of(
                    RecordWithColumns.of(existingProjectId, someRecord))
            );
            Project overwritten = storage.read(existingProjectId)
                                         .orElseThrow();
            assertThat(overwritten)
                    .isEqualTo(someRecord);
        }
    }
    //TODO:2021-07-23:yuri-sergiichuk: add tests for a pre-filled storage.
}
