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

package io.spine.server.storage.redis;

import io.spine.server.ContextSpec;
import io.spine.server.storage.RecordStorageContractTest;
import io.spine.server.storage.StorageFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@DisplayName("`RedisRecordStorage` should")
@RequiresDocker
final class RedisRecordStorageTest extends RecordStorageContractTest {

    private static final ContextSpec context = ContextSpec
            .singleTenant("RedisRecordStorageTest");

    private final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:6-alpine"))
                    .withExposedPorts(6379);

    @BeforeEach
    void startRedis() {
        startRedisContainer(redis);
    }

    @AfterEach
    void stopRedis() {
        redis.stop();
    }

    @Override
    protected StorageFactory storageFactory() {
        return RedisStorageFactory.newInstance();
    }

    @Override
    protected ContextSpec context() {
        return context;
    }

    private static void startRedisContainer(GenericContainer<?> redis) {
        redis.start();
        configureRedisProps(redis);
    }

    @SuppressWarnings("AccessOfSystemProperties" /* OK for tests. */)
    private static void configureRedisProps(GenericContainer<?> redis) {
        System.setProperty("REDIS_HOST", redis.getHost());
        System.setProperty("REDIS_PORT", String.valueOf(redis.getFirstMappedPort()));
    }
}
