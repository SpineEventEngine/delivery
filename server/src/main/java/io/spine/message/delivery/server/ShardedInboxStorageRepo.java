/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import io.spine.message.delivery.server.event.MessageWritten;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.projection.ProjectionRepository;
import io.spine.server.route.EventRouting;

/**
 * A repository of {@link ShardedInboxStorage}s.
 */
final class ShardedInboxStorageRepo
        extends ProjectionRepository<ShardIndex, ShardedInboxStorage, MessagesInShard> {

    @Override
    @OverridingMethodsMustInvokeSuper
    protected void setupEventRouting(EventRouting<ShardIndex> routing) {
        super.setupEventRouting(routing);
        routing.unicast(MessageWritten.class, ShardedInboxStorageRepo::routeMessage);
    }

    private static ShardIndex routeMessage(MessageWritten event) {
        return event
                .getMessage()
                .shardIndex();
    }
}
