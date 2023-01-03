/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.server.storage.redis;

import io.spine.core.TenantId;
import io.spine.server.storage.MessageRecordSpec;
import io.spine.server.storage.RecordSpec;
import io.spine.test.entity.Project;
import io.spine.test.entity.ProjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.google.common.collect.Lists.newArrayListWithExpectedSize;
import static com.google.common.collect.Sets.newHashSetWithExpectedSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("`MultitenantStorage` should")
class MultitenantStorageTest {

    private static final RecordSpec<ProjectId, Project, ?> recordSpec = new MessageRecordSpec<>(
            ProjectId.class, Project.class, Project::getId
    );

    private static final boolean IS_MULTITENANT = false;
    @Container
    private final GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:6-alpine")
    ).withExposedPorts(6379);

    private MultitenantStorage<TenantRecords<ProjectId, Project>> multitenantStorage;

    @BeforeEach
    void setUp() {
        redis.start();
        Config redisConfig = new Config();
        String redisAddress = String.format(
                "redis://%s:%d", redis.getContainerIpAddress(), redis.getFirstMappedPort()
        );
        redisConfig.useSingleServer()
                   .setAddress(redisAddress);
        RedissonClient client = Redisson.create(redisConfig);
        multitenantStorage =
                new MultitenantStorage<>(IS_MULTITENANT) {
                    @Override
                    TenantRecords<ProjectId, Project> createSlice(TenantId tenant) {
                        String recordsMap = tenant.getValue() + '-' + getClass().getName();
                        RMap<String, byte[]> records = client.getMap(recordsMap);
                        return new TenantRecords<>(records, recordSpec);
                    }
                };
    }

    @AfterEach
    void stopRedis() {
        redis.stop();
    }

    @Test
    @DisplayName("return same slice within single tenant and multitenant environment")
    void returnSameSlice()
            throws InterruptedException, ExecutionException {
        int numberOfTasks = 1000;
        Collection<Callable<TenantRecords<ProjectId, Project>>> tasks =
                newArrayListWithExpectedSize(numberOfTasks);

        for (int i = 0; i < numberOfTasks; i++) {
            tasks.add(() -> {
                TenantRecords<ProjectId, Project> storage =
                        multitenantStorage.currentSlice();
                return storage;
            });
        }

        List<Future<TenantRecords<ProjectId, Project>>> futures =
                executeInMultithreadedEnvironment(tasks);
        Set<TenantRecords<ProjectId, Project>> tenantRecords =
                convertFuturesToSetOfCompletedResults(futures);

        int expected = 1;
        assertEquals(expected, tenantRecords.size());
    }

    private static <R> Set<R> convertFuturesToSetOfCompletedResults(List<Future<R>> futures)
            throws ExecutionException, InterruptedException {
        Set<R> tenantRecords = newHashSetWithExpectedSize(futures.size());
        for (Future<R> future : futures) {
            tenantRecords.add(future.get());
        }
        return tenantRecords;
    }

    private static <R> List<Future<R>>
    executeInMultithreadedEnvironment(Collection<Callable<R>> tasks) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime()
                                                                       .availableProcessors() * 2);
        List<Future<R>> futures = executor.invokeAll(tasks);
        return futures;
    }
}
