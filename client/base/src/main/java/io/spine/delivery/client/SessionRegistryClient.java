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

package io.spine.delivery.client;

import com.google.protobuf.Duration;
import io.spine.delivery.event.ExpiredSessionsReleased;
import io.spine.server.delivery.PickUpOutcome;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.WorkerId;

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
     *         the ID of the worker that would like to work with the shard
     * @return the shard picked up acknowledgement event if the shard was picked up, empty otherwise
     */
    PickUpOutcome pickUpShard(ShardIndex shard, WorkerId worker);

    /**
     * Attempts to release the {@code shard}.
     *
     * <p>The same worker that picked up the shard must also release it. It is prohibited
     * to release shards that are picked up by other workers.
     *
     * @param shard
     *         the shard to be released
     * @param worker
     *         the ID of the worker that would like to release the shard
     */
    void releaseShard(ShardIndex shard, WorkerId worker);

    /**
     * Clears up the recorded {@code NodeId}s from the session records if there was no activity
     * for longer than the passed {@code inactivityPeriod}.
     *
     * <p>It may be handy if an application node hangs or gets killed — so that it is not able
     * to complete the session in a conventional way.
     *
     * @param inactivityPeriod
     *         the duration of the period after which the session is considered expired
     */
    ExpiredSessionsReleased releaseExpiredSessions(Duration inactivityPeriod);
}
