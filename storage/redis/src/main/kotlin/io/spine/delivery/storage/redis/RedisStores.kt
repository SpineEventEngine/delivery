/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package io.spine.delivery.storage.redis

import io.spine.delivery.storage.InboxStore
import io.spine.delivery.storage.ShardSessionStore
import io.spine.io.Resource
import java.io.IOException
import java.net.URL
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config

/**
 * The well-known locations of the Redisson configuration, the test configuration first.
 */
private val CONFIG_LOCATIONS = listOf(
    "redisson-test-config.yml", "redisson-test-config.yaml",
    "redisson-config.yml", "redisson-config.yaml"
)

/**
 * The Delivery stores of one Redis connection.
 *
 * Every Delivery server that connects to the same Redis database serves the same content.
 */
public class RedisStores private constructor(
    private val client: RedissonClient
) : AutoCloseable {

    /**
     * The store of the inbox messages.
     */
    public val inbox: InboxStore = RedisInboxStore(client)

    /**
     * The store of the shard session records.
     */
    public val sessions: ShardSessionStore = RedisShardSessionStore(client)

    /**
     * Closes the stores, and then the connection, even if closing a store fails.
     */
    override fun close() {
        try {
            try {
                inbox.close()
            } finally {
                sessions.close()
            }
        } finally {
            client.shutdown()
        }
    }

    public companion object {

        /**
         * Connects to Redis as configured by the first file found in the well-known
         * locations: `redisson-test-config.yml`, `redisson-test-config.yaml`,
         * `redisson-config.yml`, or `redisson-config.yaml`.
         *
         * ```kotlin
         * RedisStores.start().use { stores ->
         *     stores.inbox.write(messages)
         * }
         * ```
         *
         * @throws IllegalStateException if there is no configuration, or it cannot be read
         */
        @JvmStatic
        public fun start(): RedisStores {
            val location = CONFIG_LOCATIONS
                .map { Resource.file(it, RedisStores::class.java.classLoader) }
                .firstOrNull { it.exists() }
                ?: error("Redisson configuration not found in any of $CONFIG_LOCATIONS.")
            return start(parse(location.locate()))
        }

        /**
         * Connects to Redis with the given configuration.
         */
        @JvmStatic
        public fun start(config: Config): RedisStores {
            val client = Redisson.create(config)
            return try {
                RedisStores(client)
            } catch (e: RuntimeException) {
                client.shutdown()
                throw e
            }
        }

        private fun parse(file: URL): Config =
            try {
                Config.fromYAML(file)
            } catch (e: IOException) {
                throw IllegalStateException(
                    "Unable to read the Redisson configuration from `$file`.", e
                )
            }
    }
}
