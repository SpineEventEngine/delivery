/*
 * Copyright 2021, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.server.storage.redis;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Message;
import io.spine.io.Resource;
import io.spine.server.ContextSpec;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.StorageFactory;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.io.IOException;
import java.net.URL;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * A factory for in-memory storages.
 */
public final class RedisStorageFactory implements StorageFactory {

    /**
     * A list of well-known Redisson configuration locations.
     *
     * <p>Gives priority to test configurations while those must be used first when running
     * in the test environment.
     */
    private static final ImmutableList<String> DEFAULT_CONFIG_LOCATIONS = ImmutableList.of(
            "redisson-test-config.yml", "redisson-test-config.yaml",
            "redisson-config.yml", "redisson-config.yaml"
    );

    private final RedissonClient client;

    private RedisStorageFactory(RedissonClient client) {
        this.client = checkNotNull(client);
    }

    /**
     * Creates new instance of the factory which uses supplied {@code client} to talk to Redis.
     *
     * @return new instance of the factory
     */
    public static RedisStorageFactory newInstance(RedissonClient client) {
        return new RedisStorageFactory(client);
    }

    /**
     * Creates new instance of the factory which would serve the specified context.
     *
     * <p>Parses Redis {@link org.redisson.config.Config configuration} from
     * a {@linkplain #DEFAULT_CONFIG_LOCATIONS well-known configuration file}.
     *
     * @return new instance of the factory
     */
    public static RedisStorageFactory newInstance() {
        Config config = DEFAULT_CONFIG_LOCATIONS
                .stream()
                .map(RedisStorageFactory::localResource)
                .filter(Resource::exists)
                .map(Resource::locate)
                .findFirst()
                .map(RedisStorageFactory::parseConfig)
                .orElseThrow(() -> {
                    throw newIllegalStateException(
                            "Redisson configuration not found in any of the well-known locations: %s",
                            DEFAULT_CONFIG_LOCATIONS
                    );
                });
        return newInstance(Redisson.create(config));
    }

    private static Config parseConfig(URL configFile) {
        try {
            return Config.fromYAML(configFile);
        } catch (IOException e) {
            throw newIllegalStateException(
                    e,
                    "Unable to read Redisson YAML configuration from config file `%s`.",
                    configFile
            );
        }
    }

    private static Resource localResource(String config) {
        return Resource.file(config, RedisStorageFactory.class.getClassLoader());
    }

    @Override
    public <I, M extends Message> RedisRecordStorage<I, M>
    createRecordStorage(ContextSpec context, RecordSpec<I, M, ?> spec) {
        return new RedisRecordStorage<>(context, spec, client);
    }

    @Override
    public void close() {
        // NOP
    }
}
