/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.message.delivery.event.ShardReleased;
import io.spine.server.NodeId;
import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.ShardIndex;

import java.util.Optional;

interface SessionRegistryClient {

    Optional<ShardPickedUp> pickUpShard(ShardIndex shard, NodeId worker);

    default Optional<ShardPickedUp> pickUpShard(ShardIndex shard) {
        var worker = ServerEnvironment.instance().nodeId();
        return pickUpShard(shard, worker);
    }

    Optional<ShardReleased> releaseShard(ShardIndex shard, NodeId worker);

    default Optional<ShardReleased> releaseShard(ShardIndex shard) {
        var worker = ServerEnvironment.instance().nodeId();
        return releaseShard(shard, worker);
    }
}
