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

import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.WorkerId;

/**
 * The outcome of a {@linkplain DeliveryShardRegistry#releaseShard(ShardIndex, WorkerId) release}
 * of a shard.
 */
public enum ReleaseOutcome {

    /**
     * The worker held the shard, and its session was released.
     */
    RELEASED,

    /**
     * No worker held the shard, so there was nothing to release.
     */
    NOT_PICKED_UP,

    /**
     * Another worker held the shard, so its session was left intact.
     *
     * <p>This outcome is also reported for a release whose write was applied but whose
     * reply was lost, when another worker picked the shard before the release read
     * the record again.
     */
    HELD_BY_ANOTHER_WORKER,

    /**
     * The worker held the shard in a newer session than the one that the release found,
     * so the newer session was left intact.
     *
     * <p>The worker picked the shard again while the release was in progress. Usually,
     * the worker started a new round of delivery, and the release came late.
     *
     * <p>Rarely, a delayed retry of an earlier pick-up renewed the session instead.
     * After such a renewal, the shard stays with the worker, which can still pick it up
     * again and deliver. Other workers get the shard only after the worker releases it in
     * a later round, after the session becomes stale, or after a release of inactive
     * sessions clears it. With no processing timeout, a session never becomes stale.
     *
     * <p>This outcome is also reported for a release whose write was applied but whose
     * reply was lost, when the worker picked the shard again before the release could
     * read the record once more.
     */
    RENEWED
}
