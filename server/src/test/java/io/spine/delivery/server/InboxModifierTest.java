/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.server;

import io.spine.base.Identifier;
import io.spine.delivery.command.RemoveMessage;
import io.spine.delivery.command.RemoveMessages;
import io.spine.delivery.command.WriteMessage;
import io.spine.delivery.command.WriteMessages;
import io.spine.delivery.event.MessageRemoved;
import io.spine.delivery.event.MessageWritten;
import io.spine.delivery.event.MessagesRemoved;
import io.spine.delivery.event.MessagesWritten;
import io.spine.delivery.server.given.TestInboxMessages;
import io.spine.server.delivery.InboxMessage;
import io.spine.test.delivery.server.Something;
import io.spine.testing.server.EventSubject;
import io.spine.type.TypeUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("`InboxModifierProcess` should")
final class InboxModifierTest extends DeliveryTest {

    @Nested
    @DisplayName("handle a `WriteMessage` command")
    final class HandleWriteMessage {

        private final InboxMessage message = TestInboxMessages
                .toDeliver(Identifier.newUuid(), TypeUrl.of(Something.class));

        @BeforeEach
        void writeMessage() {
            var writeMessage = WriteMessage.newBuilder()
                    .setMessage(message)
                    .build();
            context().receivesCommand(writeMessage);
        }

        @Test
        @DisplayName("emitting `MessageWritten` event")
        void event() {
            var messageWritten = MessageWritten.newBuilder()
                    .setMessage(message)
                    .build();
            context().assertEvent(messageWritten);
        }
    }

    @Nested
    @DisplayName("handle a `WriteMessages` command")
    final class HandleWriteMessages {

        private final InboxMessage firstMessage = TestInboxMessages
                .toDeliver(Identifier.newUuid(), TypeUrl.of(Something.class));
        private final InboxMessage secondMessage = TestInboxMessages
                .toDeliver(Identifier.newUuid(), TypeUrl.of(Something.class));

        @BeforeEach
        void writeMessages() {
            var writeMessages = WriteMessages.newBuilder()
                    .addMessage(firstMessage)
                    .addMessage(secondMessage)
                    .setShard(firstMessage.shardIndex())
                    .build();
            context().receivesCommand(writeMessages);
        }

        @Test
        @DisplayName("emitting `MessagesWritten` event")
        void event() {
            var messagesWritten = MessagesWritten.newBuilder()
                    .addMessage(firstMessage)
                    .addMessage(secondMessage)
                    .setShard(firstMessage.shardIndex())
                    .build();
            context().assertEvent(messagesWritten);
        }

        @Test
        @DisplayName("emitting `MessageWritten` events for each of the removed messages")
        void removeEachMessage() {
            var firstWritten = MessageWritten.newBuilder()
                    .setMessage(firstMessage)
                    .build();
            var secondWritten = MessageWritten.newBuilder()
                    .setMessage(secondMessage)
                    .build();
            EventSubject writtenMessages = context()
                    .assertEvents()
                    .withType(MessageWritten.class);
            writtenMessages.hasSize(2);
            writtenMessages.message(0)
                           .isEqualTo(firstWritten);
            writtenMessages.message(1)
                           .isEqualTo(secondWritten);
        }
    }

    @Nested
    @DisplayName("handle a `RemoveMessage` command")
    final class HandleRemoveMessage {

        private final InboxMessage message = TestInboxMessages
                .toDeliver(Identifier.newUuid(), TypeUrl.of(Something.class));

        @BeforeEach
        void removeMessage() {
            var removeMessage = RemoveMessage.newBuilder()
                    .setMessage(message)
                    .build();
            context().receivesCommand(removeMessage);
        }

        @Test
        @DisplayName("emitting `MessageRemoved` event")
        void event() {
            var messageRemoved = MessageRemoved.newBuilder()
                    .setMessage(message)
                    .build();
            context().assertEvent(messageRemoved);
        }
    }

    @Nested
    @DisplayName("handle a `RemoveMessages` command")
    final class HandleRemoveMessages {

        private final InboxMessage firstMessage = TestInboxMessages
                .toDeliver(Identifier.newUuid(), TypeUrl.of(Something.class));
        private final InboxMessage secondMessage = TestInboxMessages
                .toDeliver(Identifier.newUuid(), TypeUrl.of(Something.class));

        @BeforeEach
        void removeMessages() {
            var removeMessages = RemoveMessages.newBuilder()
                    .addMessage(firstMessage)
                    .addMessage(secondMessage)
                    .setShard(firstMessage.shardIndex())
                    .build();
            context().receivesCommand(removeMessages);
        }

        @Test
        @DisplayName("emitting `MessagesRemoved` event")
        void event() {
            var messagesRemoved = MessagesRemoved.newBuilder()
                    .addMessage(firstMessage)
                    .addMessage(secondMessage)
                    .setShard(firstMessage.shardIndex())
                    .build();
            context().assertEvent(messagesRemoved);
        }

        @Test
        @DisplayName("emitting `MessageRemove` events for each of the removed messages")
        void removeEachMessage() {
            var firstRemoved = MessageRemoved.newBuilder()
                    .setMessage(firstMessage)
                    .build();
            var secondRemoved = MessageRemoved.newBuilder()
                    .setMessage(secondMessage)
                    .build();
            EventSubject removedMessages = context()
                    .assertEvents()
                    .withType(MessageRemoved.class);
            removedMessages.hasSize(2);
            removedMessages.message(0)
                           .isEqualTo(firstRemoved);
            removedMessages.message(1)
                           .isEqualTo(secondRemoved);
        }
    }
}
