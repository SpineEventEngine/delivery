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

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

/**
 * The port Redis listens on inside its container.
 */
private const val REDIS_PORT = 6379

/**
 * A Redis server in a container, with a client connected to it.
 */
internal class TestRedis : AutoCloseable {

    private val container: GenericContainer<*> =
        GenericContainer(DockerImageName.parse("redis:6-alpine"))
            .withExposedPorts(REDIS_PORT)
            .apply { start() }

    /**
     * The configuration of a client of this server.
     */
    val config: Config
        get() = Config().apply {
            useSingleServer().address = "redis://${container.host}:${container.firstMappedPort}"
        }

    /**
     * A client connected to this server.
     */
    val client: RedissonClient = Redisson.create(config)

    /**
     * Deletes all the data.
     */
    fun clear() {
        client.keys.flushall()
    }

    /**
     * Drops every publish/subscribe connection, as a network failure does. The clients
     * connect and subscribe again.
     */
    fun dropSubscriptions() {
        container.execInContainer("redis-cli", "CLIENT", "KILL", "TYPE", "pubsub")
    }

    override fun close() {
        client.shutdown()
        container.stop()
    }
}
