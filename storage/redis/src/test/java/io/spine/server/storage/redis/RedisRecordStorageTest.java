/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.server.storage.redis;

import io.spine.server.ContextSpec;
import io.spine.server.storage.StorageFactory;
import io.spine.server.storage.StorageTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

@DisplayName("`RedisRecordStorage` should")
final class RedisRecordStorageTest extends StorageTestBase {

    private static final ContextSpec context = ContextSpec
            .singleTenant("RedisRecordStorageTest");

    @Container
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
    protected StorageFactory getStorageFactory() {
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
        System.setProperty("REDIS_HOST", redis.getContainerIpAddress());
        System.setProperty("REDIS_PORT", String.valueOf(redis.getFirstMappedPort()));
    }
}
