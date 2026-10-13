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

import io.spine.server.delivery.ShardProcessingSession;
import io.spine.server.delivery.ShardSessionRecord;
import io.spine.server.delivery.WorkerId;

/**
 * Implementation of the shard processing session, completed by releasing its shard
 * if its worker still holds it.
 */
public final class DeliveryShardSession extends ShardProcessingSession {

    /**
     * The registry that picked the shard of the session, and releases it.
     */
    private final DeliveryShardRegistry registry;

    /**
     * The worker that picked the shard.
     */
    private final WorkerId worker;

    /**
     * Creates the session described by the given record, picked by the given registry.
     */
    DeliveryShardSession(ShardSessionRecord record, DeliveryShardRegistry registry) {
        super(record);
        this.registry = registry;
        this.worker = record.getWorker();
    }

    /**
     * Releases the shard of the session, if its worker still holds it.
     *
     * <p>The release itself leaves intact both the session of another worker and a session
     * that the same worker started while the release was in progress. So its outcome needs
     * no handling here, and is ignored.
     *
     * <p>The session does not tell its own write apart from a later renewal by the same
     * worker. So if the worker picked the shard again before the release reads the record,
     * completing this session ends the renewed one.
     */
    @Override
    @SuppressWarnings("ResultOfMethodCallIgnored" /* The outcome is informational. */)
    protected void complete() {
        registry.releaseShard(shardIndex(), worker);
    }
}
