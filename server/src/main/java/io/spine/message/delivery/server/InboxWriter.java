/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import io.spine.base.Identifier;
import io.spine.core.CommandContext;
import io.spine.logging.Logging;
import io.spine.message.delivery.server.command.WriteMessage;
import io.spine.message.delivery.server.event.MessageWritten;
import io.spine.server.command.AbstractCommandHandler;
import io.spine.server.command.Assign;

/**
 * Handles {@link WriteMessage} commands to store new {@link io.spine.server.delivery.Inbox Inbox}
 * messages.
 */
final class InboxWriter extends AbstractCommandHandler implements Logging {

    @Assign
    MessageWritten handle(WriteMessage c, CommandContext context) {
        _debug().log("Writing a new message for Inbox `%s` on `%s` request.",
                     Identifier.toString(c.getInbox()), context.actor());
        return MessageWritten
                .newBuilder()
                .setInbox(c.getInbox())
                .setTenant(c.getTenant())
                .setMessage(c.getMessage())
                .vBuild();
    }
}
