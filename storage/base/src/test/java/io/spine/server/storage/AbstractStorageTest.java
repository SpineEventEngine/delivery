/*
 * Copyright 2026, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.server.storage;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import io.spine.base.Identifier;
import io.spine.query.RecordColumn;
import io.spine.query.RecordQuery;
import io.spine.server.ContextSpec;
import io.spine.test.entity.Project;
import io.spine.test.entity.ProjectId;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.test.entity.Project.Column.name;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Abstract base for different storage tests.
 */
public abstract class AbstractStorageTest {

    private static final RecordSpec<ProjectId, Project> recordSpec = new RecordSpec<>(
            ProjectId.class, Project.class, Project::getId,
            ImmutableList.of(RecordColumn.create("name", name().type(), Project::getName))
    );

    /**
     * Obtains an instance of the {@code StorageFactory} that produces storages to test.
     */
    protected abstract StorageFactory storageFactory();

    /**
     * Returns a {@code Context} for the storage creation.
     */
    protected abstract ContextSpec context();

    @Nested
    @DisplayName("not throw exceptions when the storage is empty")
    class EmptyStorage {

        private @MonotonicNonNull RecordStorage<ProjectId, Project> storage;

        private @MonotonicNonNull StorageFactory factory;

        @BeforeEach
        void startRedis() {
            factory = storageFactory();
            storage = factory.createRecordStorage(context(), recordSpec);
        }

        @AfterEach
        void stopRedis() throws Exception {
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
                    .build();
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
                    .build();
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
                    .build();
            assertThat(storage.deleteRecord(someId))
                    .isFalse();
        }

        @Test
        @DisplayName("when calling `write`")
        void write() {
            ProjectId someId = ProjectId.newBuilder()
                    .setId(Identifier.newUuid())
                    .build();
            Project someRecord = Project.newBuilder()
                    .setId(someId)
                    .buildPartial();
            assertDoesNotThrow(() -> storage.write(someId, someRecord));
        }

        @Test
        @DisplayName("when calling `writeAllRecords`")
        void writeAll() {
            ProjectId someId = ProjectId.newBuilder()
                    .setId(Identifier.newUuid())
                    .build();
            Project someRecord = Project.newBuilder()
                    .setId(someId)
                    .buildPartial();
            assertDoesNotThrow(() ->
                    storage.writeAllRecords(ImmutableList.of(RecordWithColumns.of(someId, someRecord))));
        }
    }

    @Nested
    @DisplayName("not throw exceptions when the working with a pre-filled storage and")
    class PreFilledStorage {

        private final ProjectId existingProjectId = ProjectId.newBuilder()
                .setId(Identifier.newUuid())
                .build();
        private final Project existingProject = Project.newBuilder()
                .setId(existingProjectId)
                .setName(PreFilledStorage.class.getName())
                .buildPartial();

        private @MonotonicNonNull RecordStorage<ProjectId, Project> storage;

        private @MonotonicNonNull StorageFactory factory;

        @BeforeEach
        void startRedis() {
            factory = storageFactory();
            storage = factory.createRecordStorage(context(), recordSpec);
            storage.write(existingProjectId, existingProject);
        }

        @AfterEach
        void stopRedis() throws Exception {
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
                    .build();
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
                    .build();
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
            assertThat(storage.deleteRecord(existingProjectId))
                    .isTrue();
        }

        @Test
        @DisplayName("overwrite existing record")
        void write() {
            ProjectId someId = ProjectId.newBuilder()
                    .setId(Identifier.newUuid())
                    .build();
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
                    .build();
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
