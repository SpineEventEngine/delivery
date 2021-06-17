/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import io.spine.base.Time;
import io.spine.message.delivery.server.event.MessageWritten;
import io.spine.protobuf.Durations2;
import io.spine.server.delivery.DeliveryStrategy;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.ShardIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static io.spine.message.delivery.server.given.TestInboxMessages.toDeliver;

@DisplayName("`ShardedInboxStorage` should")
final class ShardedInboxStorageTest extends DeliveryTest {

    private static final Timestamp firstMessageTime = Time.currentTime();
    private static final Timestamp secondMessageTime =
            Timestamps.add(firstMessageTime, Durations2.seconds(5));

    private static final ShardIndex firstShard = DeliveryStrategy.newIndex(1, 3);
    private static final ShardIndex nextShard = DeliveryStrategy.newIndex(2, 3);

    private static final InboxMessage firstMessage = toDeliver(firstMessageTime, firstShard);
    private static final InboxMessage secondMessage = toDeliver(secondMessageTime, firstShard);

    private static final InboxMessage nextShardMessage = toDeliver(firstMessageTime, nextShard);

    @Nested
    @DisplayName("accumulate written messages")
    class Accumulate {

        @BeforeEach
        void receiveMessages() {
            context().receivesEvents(
                    ofMessage(secondMessage), ofMessage(firstMessage), ofMessage(nextShardMessage)
            );
        }

        @Test
        @DisplayName("for the same shard")
        void forSameShard() {
            MessagesInShard firstShardMessages = (MessagesInShard) context()
                    .assertEntityWithState(firstShard, MessagesInShard.class)
                    .actual()
                    .state();
            assertThat(firstShardMessages.getMessageList())
                    .ignoringRepeatedFieldOrder()
                    .containsExactly(firstMessage, secondMessage);

            MessagesInShard nextShardMessages = (MessagesInShard) context()
                    .assertEntityWithState(nextShard, MessagesInShard.class)
                    .actual()
                    .state();
            assertThat(nextShardMessages.getMessageList())
                    .ignoringRepeatedFieldOrder()
                    .containsExactly(nextShardMessage);
        }

        @Test
        @DisplayName("sorting them chronologically")
        void withChronologicalOrder() {
            MessagesInShard firstShardMessages = (MessagesInShard) context()
                    .assertEntityWithState(firstShard, MessagesInShard.class)
                    .actual()
                    .state();
            assertThat(firstShardMessages.getMessageList())
                    .containsExactly(firstMessage, secondMessage);
        }
    }

    private static MessageWritten ofMessage(InboxMessage message) {
        return MessageWritten.newBuilder()
                .setMessage(message)
                .vBuild();
    }
}
