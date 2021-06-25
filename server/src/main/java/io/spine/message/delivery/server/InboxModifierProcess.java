/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import io.spine.base.Identifier;
import io.spine.core.CommandContext;
import io.spine.logging.Logging;
import io.spine.message.delivery.InboxModifier;
import io.spine.message.delivery.InboxModifierId;
import io.spine.message.delivery.command.WriteMessage;
import io.spine.message.delivery.event.MessageWritten;
import io.spine.server.command.Assign;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.procman.ProcessManager;
/**
 * Handles {@link WriteMessage} commands to store new
 * {@link io.spine.server.delivery.Inbox Inbox} messages.
 */
final class InboxModifierProcess
        extends ProcessManager<InboxModifierId, InboxModifier, InboxModifier.Builder>
        implements Logging {

    @Assign
    MessageWritten handle(WriteMessage c, CommandContext context) {
        InboxMessage message = c.getMessage();
        _debug().log(
                "Writing a new message for Inbox `%s` in Shard `%s` on `%s` request.",
                Identifier.toString(message.getInboxId()), message.shardIndex(), context.actor()
        );
        return MessageWritten.newBuilder()
                .setMessage(message)
                .vBuild();
    }
}
