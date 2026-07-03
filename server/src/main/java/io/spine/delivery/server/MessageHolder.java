/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.server;

import io.spine.core.EventContext;
import io.spine.core.Subscribe;
import io.spine.delivery.InboxMessageHolder;
import io.spine.delivery.event.MessageRemoved;
import io.spine.delivery.event.MessageWritten;
import io.spine.server.delivery.InboxMessageId;
import io.spine.server.projection.Projection;

/**
 * Holds state of a particular {@link io.spine.server.delivery.InboxMessage InboxMessage}.
 */
final class MessageHolder
        extends Projection<InboxMessageId, InboxMessageHolder, InboxMessageHolder.Builder> {

    @Subscribe
    void on(MessageWritten e, EventContext context) {
        var message = e.getMessage();
        builder().setMessage(message)
                 .setShard(message.shardIndex())
                 .setInbox(message.getInboxId())
                 .setSignal(message.getSignalId())
                 .setIsEvent(message.hasEvent())
                 .setIsCommand(message.hasCommand())
                 .setLabel(message.getLabel())
                 .setStatus(message.getStatus())
                 .setReceivedAt(message.getWhenReceived())
                 .setVersion(message.getVersion());
    }

    @Subscribe
    void on(MessageRemoved e) {
        setDeleted(true);
    }
}
