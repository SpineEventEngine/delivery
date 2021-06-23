/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.message.delivery.event.ShardReleased;
import io.spine.server.NodeId;
import io.spine.server.delivery.ShardIndex;

import java.util.Optional;

/**
 * A client for working with the delivery session registry.
 */
interface SessionRegistryClient {

    /**
     * Tries to pick up a shard for working with it.
     *
     * @param shard
     *         the shard to pick up
     * @param worker
     *         the node which would like to work with the shard
     * @return the shard picked up acknowledgement event if the shard was picked up, empty otherwise
     */
    Optional<ShardPickedUp> pickUpShard(ShardIndex shard, NodeId worker);

    /**
     * Tries to release the shard.
     *
     * <p>The same node which picked up the shard must also release it. It is prohibited to release
     * shards which are picked up by other workers.
     *
     * @param shard
     *         the shard to be released
     * @param worker
     *         the node which would like to release the shard
     * @return the shard released acknowledgement event if the shard was released, empty otherwise
     */
    Optional<ShardReleased> releaseShard(ShardIndex shard, NodeId worker);
}
