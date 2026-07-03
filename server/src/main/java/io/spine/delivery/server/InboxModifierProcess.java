/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.server;

import io.spine.base.Identifier;
import io.spine.core.CommandContext;
import io.spine.logging.Logging;
import io.spine.delivery.InboxModifier;
import io.spine.delivery.InboxModifierId;
import io.spine.delivery.command.RemoveMessage;
import io.spine.delivery.command.RemoveMessages;
import io.spine.delivery.command.WriteMessage;
import io.spine.delivery.command.WriteMessages;
import io.spine.delivery.event.MessageRemoved;
import io.spine.delivery.event.MessageWritten;
import io.spine.delivery.event.MessagesRemoved;
import io.spine.delivery.event.MessagesWritten;
import io.spine.server.command.Assign;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.event.React;
import io.spine.server.procman.ProcessManager;

import java.util.stream.Collectors;

/**
 * Handles commands to modify {@link io.spine.server.delivery.Inbox Inbox} messages.
 */
final class InboxModifierProcess
        extends ProcessManager<InboxModifierId, InboxModifier, InboxModifier.Builder>
        implements Logging {

    @Assign
    MessageWritten handle(WriteMessage c, CommandContext context) {
        var message = c.getMessage();
        _debug().log(
                "Writing a new message for Inbox `%s` in Shard `%s` on `%s` request.",
                Identifier.toString(message.getInboxId()), message.shardIndex(), context.actor()
        );
        return MessageWritten.newBuilder()
                .setMessage(message)
                .build();
    }

    @Assign
    MessagesWritten handle(WriteMessages c, CommandContext context) {
        ShardIndex shard = c.getShard();
        var messages = c.getMessageList();
        _debug().log(
                "Writing %d new messages in Shard `%s` on `%s` request.",
                messages.size(), shard, context.actor()
        );
        return MessagesWritten.newBuilder()
                .addAllMessage(messages)
                .setShard(shard)
                .build();
    }

    @React
    Iterable<MessageWritten> on(MessagesWritten e) {
        return e.getMessageList()
                .stream()
                .map(InboxModifierProcess::writtenMessage)
                .collect(Collectors.toList());
    }

    private static MessageWritten writtenMessage(InboxMessage message) {
        return MessageWritten.newBuilder()
                .setMessage(message)
                .build();
    }

    @Assign
    MessageRemoved handle(RemoveMessage c, CommandContext context) {
        var message = c.getMessage();
        _debug().log(
                "Removing a message from Inbox `%s` in Shard `%s` on `%s` request.",
                Identifier.toString(message.getInboxId()), message.shardIndex(), context.actor()
        );
        return MessageRemoved.newBuilder()
                .setMessage(message)
                .build();
    }

    @Assign
    MessagesRemoved handle(RemoveMessages c, CommandContext context) {
        ShardIndex shard = c.getShard();
        var messages = c.getMessageList();
        _debug().log(
                "Removing %d messages in Shard `%s` on `%s` request.",
                messages.size(), shard, context.actor()
        );
        return MessagesRemoved.newBuilder()
                .addAllMessage(messages)
                .setShard(shard)
                .build();
    }

    @React
    Iterable<MessageRemoved> on(MessagesRemoved e) {
        return e.getMessageList()
                .stream()
                .map(InboxModifierProcess::removedMessage)
                .collect(Collectors.toList());
    }

    private static MessageRemoved removedMessage(InboxMessage message) {
        return MessageRemoved.newBuilder()
                .setMessage(message)
                .build();
    }
}
