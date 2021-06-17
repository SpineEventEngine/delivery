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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("`ShardedInboxStorage` should")
final class ShardedInboxStorageTest extends DeliveryTest {

    private final InboxMessage message = TestInboxMessages
            .toDeliver(Identifier.newUuid(), TypeUrl.of(Something.class));

    @Nested
    @DisplayName("accumulate written messages")
    class Accumulate {

        @Test
        @DisplayName("for the same shard")
        void forSameShard() {
//TODO:2021-06-17:ysergiichuk: implement
        }

        @Test
        @DisplayName("")
        void withChronologicalOrder() {
//TODO:2021-06-17:ysergiichuk: implement
        }
    }

    @Test
    @DisplayName("accumulate written messages")
    void accumulateMessages() {
        var shard = message.shardIndex();
        var messageWritten = MessageWritten.newBuilder()
                .setMessage(message)
                .vBuild();
        context().receivesEvent(messageWritten);

        MessagesInShard expected = MessagesInShard.newBuilder()
                .setId(shard)
                .addMessage(message)
                .vBuild();
        context().assertState(shard, expected);
    }
}
