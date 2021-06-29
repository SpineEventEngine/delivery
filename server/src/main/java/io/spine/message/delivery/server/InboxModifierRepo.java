/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import io.spine.message.delivery.InboxModifier;
import io.spine.message.delivery.InboxModifierId;
import io.spine.message.delivery.command.RemoveMessage;
import io.spine.message.delivery.command.RemoveMessages;
import io.spine.message.delivery.command.WriteMessage;
import io.spine.message.delivery.command.WriteMessages;
import io.spine.message.delivery.event.MessagesRemoved;
import io.spine.message.delivery.event.MessagesWritten;
import io.spine.server.procman.ProcessManagerRepository;
import io.spine.server.route.CommandRouting;
import io.spine.server.route.EventRouting;

/**
 * Routes all {@link WriteMessage} commands to the {@link InboxModifierProcess}.
 */
final class InboxModifierRepo
        extends ProcessManagerRepository<InboxModifierId, InboxModifierProcess, InboxModifier> {

    /**
     * The only writer in the context.
     */
    static final InboxModifierId writer = InboxModifierId.newBuilder()
            .setValue("Homer")
            .vBuild();

    @Override
    protected void setupCommandRouting(CommandRouting<InboxModifierId> routing) {
        routing.route(WriteMessage.class, (message, context) -> writer)
               .route(WriteMessages.class, (message, context) -> writer)
               .route(RemoveMessage.class, (message, context) -> writer)
               .route(RemoveMessages.class, (message, context) -> writer);
    }

    @Override
    @OverridingMethodsMustInvokeSuper
    protected void setupEventRouting(EventRouting<InboxModifierId> routing) {
        super.setupEventRouting(routing);
        routing.unicast(MessagesRemoved.class, e -> writer)
               .unicast(MessagesWritten.class, e -> writer);
    }
}
