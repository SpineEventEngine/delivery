/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.server.storage.redis;

import com.google.common.collect.Iterators;
import io.spine.base.Identifier;
import io.spine.query.RecordQuery;
import io.spine.server.ContextSpec;
import io.spine.server.storage.MessageRecordSpec;
import io.spine.server.storage.RecordSpec;
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

@DisplayName("`RedisRecordStorage` should")
final class RedisRecordStorageTest {

    private static final RecordSpec<ProjectId, Project, ?> recordSpec = new MessageRecordSpec<>(
            ProjectId.class, Project.class, Project::getId
    );
    private static final ContextSpec context = ContextSpec.singleTenant("RedisRecordStorageTest");

    @Nested
    @DisplayName("not throw exceptions when the storage is empty")
    class EmptyStorage {

        @Container
        private final GenericContainer<?> redis = new GenericContainer<>(
                DockerImageName.parse("redis:6-alpine")
        ).withExposedPorts(6379);
        private @MonotonicNonNull RedisRecordStorage<ProjectId, Project> storage;

        @BeforeEach
        void startRedis() {
            startRedisContainer(redis);
            storage = RedisStorageFactory.newInstance().createRecordStorage(context, recordSpec);
        }

        @AfterEach
        void stopRedis() {
            redis.stop();
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

        //TODO:2021-07-23:yuri-sergiichuk: add more tests for empty storage.
    }

    //TODO:2021-07-23:yuri-sergiichuk: add tests for a pre-filled storage.

    private static void startRedisContainer(GenericContainer<?> redis) {
        redis.start();
        configureRedisProps(redis);
    }

    @SuppressWarnings("AccessOfSystemProperties" /* OK for tests. */)
    private static void configureRedisProps(GenericContainer<?> redis) {
        System.setProperty("REDIS_HOST", redis.getContainerIpAddress());
        System.setProperty("REDIS_PORT", String.valueOf(redis.getFirstMappedPort()));
    }
}
