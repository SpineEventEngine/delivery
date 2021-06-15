/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import io.spine.message.delivery.server.event.MessageWritten;
import io.spine.server.delivery.InboxId;
import io.spine.server.projection.ProjectionRepository;
import io.spine.server.route.EventRouting;

/**
 * A repository of {@link InboxStorageState}s.
 */
final class InboxStorageRepo
        extends ProjectionRepository<InboxId, InboxStorageState, InboxStorage> {

    @Override
    @OverridingMethodsMustInvokeSuper
    protected void setupEventRouting(EventRouting<InboxId> routing) {
        super.setupEventRouting(routing);
        routing.unicast(MessageWritten.class, MessageWritten::getInbox);
    }
}
