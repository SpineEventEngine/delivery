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

package io.spine.delivery.server;

import io.spine.delivery.storage.InboxStore;
import io.spine.delivery.storage.ShardSessionStore;
import io.spine.delivery.storage.hazelcast.HazelcastStores;
import io.spine.delivery.storage.memory.InMemoryInboxStore;
import io.spine.delivery.storage.memory.InMemoryShardSessionStore;
import io.spine.delivery.storage.redis.RedisStores;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The stores of a Delivery node.
 *
 * <p>In a distributed mode, both stores share one connection to the backend, which closes
 * with them.
 */
final class Stores implements AutoCloseable {

    private final InboxStore inbox;
    private final ShardSessionStore sessions;
    private final Runnable closer;

    private Stores(InboxStore inbox, ShardSessionStore sessions, Runnable closer) {
        this.inbox = checkNotNull(inbox);
        this.sessions = checkNotNull(sessions);
        this.closer = checkNotNull(closer);
    }

    /**
     * Creates the stores that keep the data in the memory of this process.
     */
    static Stores inMemory() {
        var inbox = new InMemoryInboxStore();
        var sessions = new InMemoryShardSessionStore();
        return new Stores(inbox, sessions, () -> {
            try {
                inbox.close();
            } finally {
                sessions.close();
            }
        });
    }

    /**
     * Starts an embedded Hazelcast member, and creates the stores that keep the data in
     * the cluster it joins.
     */
    static Stores hazelcast() {
        var stores = HazelcastStores.start();
        return new Stores(stores.getInbox(), stores.getSessions(), stores::close);
    }

    /**
     * Connects to Redis as {@code redisson-config.yaml} tells, and creates the stores that
     * keep the data in its database.
     */
    static Stores redis() {
        var stores = RedisStores.start();
        return new Stores(stores.getInbox(), stores.getSessions(), stores::close);
    }

    /**
     * Returns the store of the inbox messages.
     */
    InboxStore inbox() {
        return inbox;
    }

    /**
     * Returns the store of the shard sessions.
     */
    ShardSessionStore sessions() {
        return sessions;
    }

    /**
     * Closes the stores, and then their connection to the backend, if any.
     */
    @Override
    public void close() {
        closer.run();
    }
}
