/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import com.google.protobuf.Duration;
import io.spine.message.delivery.event.ExpiredSessionsReleased;
import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.WorkerId;

import java.util.Optional;

/**
 * A client for working with the delivery session registry.
 */
public interface SessionRegistryClient {

    /**
     * Tries to pick up a {@code shard} for working with it.
     *
     * @param shard
     *         the shard to pick up
     * @param worker
     *         the ID of the worker which would like to work with the shard
     * @return the shard picked up acknowledgement event if the shard was picked up, empty otherwise
     */
    Optional<ShardPickedUp> pickUpShard(ShardIndex shard, WorkerId worker);

    /**
     * Attempts to release the {@code shard}.
     *
     * <p>The same worker which picked up the shard must also release it. It is prohibited
     * to release shards which are picked up by other workers.
     *
     * @param shard
     *         the shard to be released
     * @param worker
     *         the ID of the worker which would like to release the shard
     */
    void releaseShard(ShardIndex shard, WorkerId worker);

    /**
     * Clears up the recorded {@code NodeId}s from the session records if there was no activity
     * for longer than passed {@code inactivityPeriod}.
     *
     * <p>It may be handy if an application node hangs or gets killed — so that it is not able
     * to complete the session in a conventional way.
     *
     * @param inactivityPeriod
     *         the duration of the period after which the session is considered expired
     */
    ExpiredSessionsReleased releaseExpiredSessions(Duration inactivityPeriod);
}
