/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import io.spine.base.Identifier;
import io.spine.message.delivery.server.event.MessageWritten;
import io.spine.message.delivery.server.given.TestInboxMessages;
import io.spine.server.delivery.InboxMessage;
import io.spine.test.message.delivery.server.Something;
import io.spine.type.TypeUrl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("`InboxStorageState` should")
final class InboxStorageStateTest extends DeliveryTest {

    private final InboxMessage message = TestInboxMessages
            .toDeliver(Identifier.newUuid(), TypeUrl.of(Something.class));

    @Test
    @DisplayName("accumulate written messages")
    void accumulateMessages() {
        var inbox = message.getInboxId();
        var messageWritten = MessageWritten.newBuilder()
                .setMessage(message)
                .setInbox(inbox)
                .vBuild();
        context().receivesEvent(messageWritten);

        InboxStorage expected = InboxStorage.newBuilder()
                .setId(inbox)
                .addMessage(message)
                .vBuild();
        context().assertState(inbox, expected);
    }
}
