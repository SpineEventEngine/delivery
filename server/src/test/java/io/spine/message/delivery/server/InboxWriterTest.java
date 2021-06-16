/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import io.spine.base.Identifier;
import io.spine.core.TenantId;
import io.spine.message.delivery.server.command.WriteMessage;
import io.spine.message.delivery.server.event.MessageWritten;
import io.spine.message.delivery.server.given.TestInboxMessages;
import io.spine.server.delivery.InboxMessage;
import io.spine.test.message.delivery.server.Something;
import io.spine.type.TypeUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("`InboxWriter` should")
final class InboxWriterTest extends DeliveryTest {

    @Nested
    @DisplayName("handle a `WriteMessage` command")
    class HandleWriteMessage {

        private final InboxMessage message = TestInboxMessages
                .toDeliver(Identifier.newUuid(), TypeUrl.of(Something.class));
        private final TenantId tenant = TenantId.newBuilder()
                .setValue(Identifier.newUuid())
                .vBuild();

        @BeforeEach
        void writeMessage() {
            var writeMessage = WriteMessage.newBuilder()
                    .setMessage(message)
                    .setTenant(tenant)
                    .setInbox(message.getInboxId())
                    .vBuild();
            context().receivesCommand(writeMessage);
        }

        @Test
        @DisplayName("emitting `MessageWritten` event")
        void event() {
            var messageWritten = MessageWritten.newBuilder()
                    .setMessage(message)
                    .setTenant(tenant)
                    .setInbox(message.getInboxId())
                    .vBuild();
            context().assertEvent(messageWritten);
        }
    }
}
