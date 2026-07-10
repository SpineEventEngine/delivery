/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
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
 * A factory for Redis-based storages.
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
    createRecordStorage(ContextSpec context, RecordSpec<I, M> spec) {
        return new RedisRecordStorage<>(context, spec, client);
    }

    @Override
    public boolean isOpen() {
        return !client.isShutdown();
    }

    @Override
    public void close() {
        // NOP
    }
}
