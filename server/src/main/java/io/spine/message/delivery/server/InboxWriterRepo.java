/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import io.spine.message.delivery.InboxWriter;
import io.spine.message.delivery.InboxWriterId;
import io.spine.message.delivery.command.WriteMessage;
import io.spine.server.procman.ProcessManagerRepository;
import io.spine.server.route.CommandRouting;

/**
 * Routes all {@link WriteMessage} commands to the {@link InboxWriterProcess}.
 */
final class InboxWriterRepo
        extends ProcessManagerRepository<InboxWriterId, InboxWriterProcess, InboxWriter> {

    /**
     * The only writer in the context.
     */
    static final InboxWriterId writer = InboxWriterId.newBuilder()
            .setValue("Homer")
            .vBuild();

    @Override
    protected void setupCommandRouting(CommandRouting<InboxWriterId> routing) {
        routing.route(WriteMessage.class, (message, context) -> writer);
    }
}
