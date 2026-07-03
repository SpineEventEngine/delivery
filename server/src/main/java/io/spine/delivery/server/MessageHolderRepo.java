/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.server;

import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import io.spine.delivery.InboxMessageHolder;
import io.spine.delivery.event.MessageRemoved;
import io.spine.delivery.event.MessageWritten;
import io.spine.server.delivery.InboxMessageId;
import io.spine.server.projection.ProjectionRepository;
import io.spine.server.route.EventRouting;

/**
 * A repository of {@link MessageHolder}s.
 */
final class MessageHolderRepo
        extends ProjectionRepository<InboxMessageId, MessageHolder, InboxMessageHolder> {

    @Override
    @OverridingMethodsMustInvokeSuper
    protected void setupEventRouting(EventRouting<InboxMessageId> routing) {
        super.setupEventRouting(routing);
        routing.unicast(MessageWritten.class, MessageWritten::messageId);
        routing.unicast(MessageRemoved.class, MessageRemoved::messageId);
    }
}
