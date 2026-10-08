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

package io.spine.delivery.storage.hazelcast

import com.hazelcast.config.Config
import com.hazelcast.config.EvictionPolicy
import com.hazelcast.config.InMemoryFormat
import com.hazelcast.config.MapConfig
import com.hazelcast.core.Hazelcast
import com.hazelcast.core.HazelcastInstance
import com.hazelcast.core.LifecycleEvent.LifecycleState.MERGED
import com.hazelcast.cluster.MembershipEvent
import com.hazelcast.cluster.MembershipListener
import io.spine.delivery.storage.InboxStore
import io.spine.delivery.storage.MissedChangeListeners
import io.spine.delivery.storage.ShardSessionStore
import io.spine.logging.WithLogging

/**
 * The name of the map that holds the inbox, one entry per shard.
 */
public const val INBOX_MAP: String = "delivery-inbox"

/**
 * The name of the map that holds the shard session records.
 */
public const val SESSIONS_MAP: String = "delivery-sessions"

/**
 * The Delivery stores of one embedded Hazelcast member.
 *
 * Members discover each other as their configuration specifies, by IP multicast under
 * the cluster name `delivery` with the `hazelcast.yaml` of this module. Every member holds
 * a share of the data, and a synchronous backup of another member's share, so that each
 * Delivery server of the cluster serves the same content.
 *
 * Notifications of changes that a member sent right before it crashed may be lost, and
 * a lost partition loses its entries without notifications. So the stores report missed
 * changes when a member leaves the cluster, when a partition is lost, and after
 * a split-brain merge.
 */
public class HazelcastStores private constructor(
    internal val instance: HazelcastInstance
) : AutoCloseable, WithLogging {

    private val missed = MissedChangeListeners()

    /**
     * The store of the inbox messages.
     */
    public val inbox: InboxStore =
        HazelcastInboxStore(instance.getMap(INBOX_MAP), missed)

    /**
     * The store of the shard session records.
     */
    public val sessions: ShardSessionStore =
        HazelcastShardSessionStore(instance.getMap(SESSIONS_MAP), missed)

    init {
        instance.cluster.addMembershipListener(object : MembershipListener {
            override fun memberAdded(event: MembershipEvent) = Unit

            override fun memberRemoved(event: MembershipEvent) {
                logger.atWarning().log { "The Hazelcast member `${event.member}` left." }
                missed.missed()
            }
        })
        instance.lifecycleService.addLifecycleListener { event ->
            if (event.state == MERGED) {
                logger.atWarning().log { "The Hazelcast member merged after a split-brain." }
                missed.missed()
            }
        }
        instance.partitionService.addPartitionLostListener {
            logger.atError().log {
                "The Hazelcast partition ${it.partitionId} lost its data" +
                        " (the lost replica index is ${it.lostBackupCount})."
            }
            missed.missed()
        }
    }

    /**
     * Closes the stores, and shuts the member down.
     *
     * The member is shut down even if closing a store fails, or if the member is no longer
     * running.
     */
    override fun close() {
        try {
            if (instance.lifecycleService.isRunning) {
                try {
                    inbox.close()
                } finally {
                    sessions.close()
                }
            }
        } finally {
            missed.clear()
            instance.shutdown()
        }
    }

    public companion object {

        /**
         * Starts a member with the configuration that Hazelcast loads: the `hazelcast.yaml`
         * of this module, or the file passed in `-Dhazelcast.config`, with the overrides
         * of the `HZ_*` environment variables and the `hz.*` system properties.
         *
         * ```kotlin
         * HazelcastStores.start().use { stores ->
         *     stores.inbox.write(messages)
         * }
         * ```
         */
        @JvmStatic
        public fun start(): HazelcastStores = start(Config.load())

        /**
         * Starts a member with the given configuration, adding the maps and
         * the serialization of the Delivery stores to it.
         */
        @JvmStatic
        public fun start(config: Config): HazelcastStores {
            val instance = Hazelcast.newHazelcastInstance(configure(config))
            return try {
                HazelcastStores(instance)
            } catch (e: RuntimeException) {
                instance.shutdown()
                throw e
            }
        }

        /**
         * Adds the maps and the serialization of the Delivery stores to the configuration.
         *
         * A map configuration of the same name in the given configuration is replaced.
         */
        @JvmStatic
        public fun configure(config: Config): Config {
            config.addMapConfig(
                MapConfig(INBOX_MAP)
                    .setInMemoryFormat(InMemoryFormat.OBJECT)
                    .setBackupCount(1)
                    .setAsyncBackupCount(0)
            )
            config.addMapConfig(
                MapConfig(SESSIONS_MAP)
                    .setInMemoryFormat(InMemoryFormat.BINARY)
                    .setBackupCount(1)
                    .setAsyncBackupCount(0)
                    .setTimeToLiveSeconds(0)
                    .setMaxIdleSeconds(0)
                    .apply { evictionConfig.evictionPolicy = EvictionPolicy.NONE }
            )
            config.serializationConfig
                .addDataSerializableFactory(FACTORY_ID, DeliverySerializableFactory())
            return config
        }
    }
}
