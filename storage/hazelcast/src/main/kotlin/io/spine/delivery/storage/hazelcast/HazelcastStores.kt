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
 * The name of the map that holds the inbox messages, with one entry per shard.
 */
public const val INBOX_MAP: String = "delivery-inbox"

/**
 * The name of the map that holds the shard session records.
 */
public const val SESSIONS_MAP: String = "delivery-sessions"

/**
 * The inbox and session stores of one Hazelcast member, which runs inside this process.
 *
 * The members find each other as their configuration specifies. With the `hazelcast.yaml`
 * of this module, they do so by IP multicast, under the cluster name `delivery`. Together,
 * the members of a cluster hold the data: each of them holds a share of it, and a backup
 * copy of a share of another member. When a member leaves, the others keep all the data,
 * and every member reads the same data.
 *
 * In some cases, the stores cannot be sure that they reported every change:
 *  - a member may leave right after a change, before telling the other members about it;
 *  - a part of the data may be lost, when a member and the holder of its backup copy fail
 *    together, which removes entries without reporting them;
 *  - two parts of a cluster may join again after a network split, and keep only one part's
 *    version of each entry.
 *
 * In each of these cases, the stores report that they may have missed changes.
 *
 * @param instance The member that holds the data of the stores, together with the other
 *   members of its cluster.
 */
public class HazelcastStores private constructor(
    private val instance: HazelcastInstance
) : AutoCloseable, WithLogging {

    /**
     * The listeners of the changes that the stores may have missed, shared by both stores.
     */
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
        // A member may have left right after a change, before telling the others about it.
        instance.cluster.addMembershipListener(object : MembershipListener {
            override fun memberAdded(event: MembershipEvent) = Unit

            override fun memberRemoved(event: MembershipEvent) {
                logger.atWarning().log { "The Hazelcast member `${event.member}` left." }
                missed.missed()
            }
        })
        // The cluster joined again after a network split, keeping one version of each entry.
        instance.lifecycleService.addLifecycleListener { event ->
            if (event.state == MERGED) {
                logger.atWarning().log {
                    "The Hazelcast member joined its cluster again after a network split."
                }
                missed.missed()
            }
        }
        // A part of the data was lost, without reporting the removed entries.
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
         * Starts a member with the given configuration, after adding the maps and
         * the serialization of the stores to it.
         *
         * If creating the stores fails, the member is shut down.
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
         * Adds the maps and the serialization of the stores to the configuration.
         *
         * The map of the inbox keeps each shard as an object, so that the operations on
         * a shard change it in place, without serializing it. The map of the session records
         * keeps them as bytes, so that its compare-and-set compares the bytes, and never
         * removes a record on its own. Each map has one backup copy, which is updated before
         * an operation completes.
         *
         * A configuration of a map with the same name in the given configuration is replaced.
         *
         * @return The given configuration.
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
