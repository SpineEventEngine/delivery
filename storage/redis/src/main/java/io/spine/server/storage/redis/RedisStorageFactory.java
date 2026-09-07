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

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Message;
import io.spine.io.Resource;
import io.spine.server.ContextSpec;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.StorageFactory;
import io.spine.server.storage.StorageGroup;
import org.jspecify.annotations.Nullable;
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
     * Creates a new instance of the factory that uses the supplied {@code client} to talk to Redis.
     *
     * @return new instance of the factory
     */
    public static RedisStorageFactory newInstance(RedissonClient client) {
        return new RedisStorageFactory(client);
    }

    /**
     * Creates a new instance of the factory that would serve the specified context.
     *
     * <p>Parses Redis {@link org.redisson.config.Config configuration} from
     * a {@linkplain #DEFAULT_CONFIG_LOCATIONS well-known configuration file}.
     *
     * @return new instance of the factory
     */
    public static RedisStorageFactory newInstance() {
        var config = DEFAULT_CONFIG_LOCATIONS
                .stream()
                .map(RedisStorageFactory::localResource)
                .filter(Resource::exists)
                .map(Resource::locate)
                .findFirst()
                .map(RedisStorageFactory::parseConfig)
                .orElseThrow(() -> newIllegalStateException(
                        "Redisson configuration not found in any of the well-known locations: %s",
                        DEFAULT_CONFIG_LOCATIONS
                ));
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

    /**
     * {@inheritDoc}
     *
     * <p>A storage belonging to a {@linkplain StorageGroup group} — a per-entity
     * history — is allocated a distinct Redis map, its name composed of the group
     * name and the simple name of the record type. Storages outside any group are
     * named after the {@linkplain RecordSpec#sourceType() source type} of the record
     * specification. See {@link FlatTenantStorage} for the naming details.
     */
    @Override
    public <I, M extends Message> RedisRecordStorage<I, M>
    createRecordStorage(ContextSpec context,
                        RecordSpec<I, M> spec,
                        @Nullable StorageGroup group) {
        return new RedisRecordStorage<>(context, spec, group, client);
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
