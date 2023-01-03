/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import io.spine.base.Identifier;
import io.spine.message.delivery.InboxMessageHolder;
import io.spine.message.delivery.event.MessageRemoved;
import io.spine.message.delivery.event.MessageWritten;
import io.spine.message.delivery.server.given.TestInboxMessages;
import io.spine.server.delivery.InboxMessage;
import io.spine.test.message.delivery.server.Something;
import io.spine.type.TypeUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("`MessageHolder` should")
final class MessageHolderTest extends DeliveryTest {

    private final InboxMessage message = TestInboxMessages
            .toDeliver(Identifier.newUuid(), TypeUrl.of(Something.class));

    @BeforeEach
    void messageWritten() {
        var messageWritten = MessageWritten.newBuilder()
                .setMessage(message)
                .vBuild();
        context().receivesEvent(messageWritten);
    }

    @Test
    @DisplayName("handle `MessageWritten` event")
    void handleWrites() {
        var expected = InboxMessageHolder.newBuilder()
                .setId(message.getId())
                .setMessage(message)
                .setShard(message.shardIndex())
                .setInbox(message.getInboxId())
                .setSignal(message.getSignalId())
                .setIsEvent(false)
                .setIsCommand(false)
                .setLabel(message.getLabel())
                .setStatus(message.getStatus())
                .setReceivedAt(message.getWhenReceived())
                .setVersion(message.getVersion())
                .vBuild();
        context().assertState(message.getId(), expected);
    }

    @Test
    @DisplayName("handle `MessageRemoved` event")
    void handleRemovals() {
        var messageRemoved = MessageRemoved.newBuilder()
                .setMessage(message)
                .vBuild();
        context().receivesEvent(messageRemoved);
        context().assertEntity(message.getId(), MessageHolder.class)
                 .deletedFlag()
                 .isTrue();
    }
}
