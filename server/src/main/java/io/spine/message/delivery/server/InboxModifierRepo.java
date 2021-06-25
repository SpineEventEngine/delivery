/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import io.spine.message.delivery.InboxModifier;
import io.spine.message.delivery.InboxModifierId;
import io.spine.message.delivery.command.RemoveMessage;
import io.spine.message.delivery.command.RemoveMessages;
import io.spine.message.delivery.command.WriteMessage;
import io.spine.message.delivery.command.WriteMessages;
import io.spine.server.procman.ProcessManagerRepository;
import io.spine.server.route.CommandRouting;

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
}
