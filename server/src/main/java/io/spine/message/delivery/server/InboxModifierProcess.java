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
import io.spine.message.delivery.command.RemoveMessage;
import io.spine.message.delivery.command.RemoveMessages;
import io.spine.message.delivery.command.WriteMessage;
import io.spine.message.delivery.command.WriteMessages;
import io.spine.message.delivery.event.MessageRemoved;
import io.spine.message.delivery.event.MessageWritten;
import io.spine.message.delivery.event.MessagesRemoved;
import io.spine.message.delivery.event.MessagesWritten;
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
                .vBuild();
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
                .vBuild();
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
                .vBuild();
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
                .vBuild();
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
                .vBuild();
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
                .vBuild();
    }
}
