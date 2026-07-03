/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery;

import com.google.common.truth.Truth8;
import com.google.protobuf.util.Timestamps;
import io.spine.delivery.client.SimpleDeliveryClient;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.Page;
import io.spine.server.delivery.PickUpOutcome;
import io.spine.server.delivery.ShardIndex;
import io.spine.test.delivery.client.Something;
import io.spine.type.TypeUrl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.delivery.TestEnv.SHARD;
import static io.spine.delivery.TestEnv.WORKER;
import static io.spine.delivery.TestEnv.generate;
import static io.spine.delivery.TestEnv.newMessage;
import static io.spine.delivery.client.given.TestInboxMessages.toDeliver;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Tag("integration")
@DisplayName("Distributed Liquor servers should")
public class ConsistencyTest extends DistributedTest {

    @ParameterizedTest
    @MethodSource("clients")
    @DisplayName("pick up on one node and release on another")
    void pickUpAndRelease(Supplier<SimpleDeliveryClient> first,
                          Supplier<SimpleDeliveryClient> second) {
        SimpleDeliveryClient client1 = first.get();
        SimpleDeliveryClient client2 = second.get();

        PickUpOutcome outcome = client1.pickUpShard(SHARD, WORKER);
        assertThat(outcome.hasSession())
                .isTrue();
        assertDoesNotThrow(() -> client2.releaseShard(SHARD, WORKER));
    }

    @ParameterizedTest
    @MethodSource("clients")
    @DisplayName("do not pick up on another node if already picked up")
    void doesNotPickUpShard(Supplier<SimpleDeliveryClient> first,
                            Supplier<SimpleDeliveryClient> second) {
        SimpleDeliveryClient client1 = first.get();
        SimpleDeliveryClient client2 = second.get();

        PickUpOutcome firstAttempt = client1.pickUpShard(SHARD, WORKER);
        assertThat(firstAttempt.hasSession())
                .isTrue();
        PickUpOutcome secondAttempt = client2.pickUpShard(SHARD, WORKER);
        assertThat(secondAttempt.hasAlreadyPicked())
                .isTrue();
    }

    @ParameterizedTest
    @MethodSource("clients")
    @DisplayName("pick up, release, and allow a new pick up")
    void allowToPickUpReleasedShard(Supplier<SimpleDeliveryClient> first,
                                    Supplier<SimpleDeliveryClient> second) {
        SimpleDeliveryClient client1 = first.get();
        SimpleDeliveryClient client2 = second.get();

        PickUpOutcome firstAttempt = client1.pickUpShard(SHARD, WORKER);
        assertThat(firstAttempt.hasSession())
                .isTrue();
        PickUpOutcome secondAttempt = client2.pickUpShard(SHARD, WORKER);
        assertThat(secondAttempt.hasAlreadyPicked())
                .isTrue();
        assertDoesNotThrow(() -> client2.releaseShard(SHARD, WORKER));
        PickUpOutcome thirdAttempt = client1.pickUpShard(SHARD, WORKER);
        assertThat(thirdAttempt.hasSession())
                .isTrue();
    }

    @ParameterizedTest
    @MethodSource("clients")
    @DisplayName("write a message to the Inbox")
    void writeMessage(Supplier<SimpleDeliveryClient> first,
                      Supplier<SimpleDeliveryClient> second) {
        SimpleDeliveryClient client1 = first.get();
        SimpleDeliveryClient client2 = second.get();

        InboxMessage message = newMessage();
        client1.writeMessage(message);

        Optional<InboxMessage> readMessage = client2.find(message.getId());
        Truth8.assertThat(readMessage)
              .isPresent();
    }

    @ParameterizedTest
    @MethodSource("clients")
    @DisplayName("write messages to the Inbox in bulk")
    void writeMessages(Supplier<SimpleDeliveryClient> first,
                       Supplier<SimpleDeliveryClient> second) {
        SimpleDeliveryClient client1 = first.get();
        SimpleDeliveryClient client2 = second.get();

        InboxMessage firstMessage = newMessage();
        InboxMessage secondMessage = newMessage();
        ShardIndex shard = firstMessage.shardIndex();
        client1.writeMessages(
                shard, ImmutableList.of(firstMessage, secondMessage)
        );
        Page<InboxMessage> writtenMessages = client2.readAll(shard, 10);
        assertThat(writtenMessages.size())
                .isEqualTo(2);
    }

    @ParameterizedTest
    @MethodSource("clients")
    @DisplayName("read messages in pages")
    void readPages(Supplier<SimpleDeliveryClient> first,
                   Supplier<SimpleDeliveryClient> second) {
        SimpleDeliveryClient client1 = first.get();
        SimpleDeliveryClient client2 = second.get();

        List<InboxMessage> messages = generate(30);
        ShardIndex shard = messages.get(0)
                                   .shardIndex();
        client1.writeMessages(shard, messages);

        int pageSize = 10;
        Page<InboxMessage> writtenMessages = client2.readAll(shard, pageSize);
        assertThat(writtenMessages.size())
                .isEqualTo(pageSize);
        Truth8.assertThat(writtenMessages.next())
              .isPresent();
        Truth8.assertThat(writtenMessages.next())
              .isPresent();
        Truth8.assertThat(writtenMessages.next())
              .isEmpty();
    }

    @ParameterizedTest
    @MethodSource("clients")
    @DisplayName("read newest message to deliver")
    void readNewest(Supplier<SimpleDeliveryClient> first,
                    Supplier<SimpleDeliveryClient> second) {
        SimpleDeliveryClient client1 = first.get();
        SimpleDeliveryClient client2 = second.get();

        InboxMessage olderMessage = toDeliver(
                Timestamps.fromSeconds(100000L),
                TypeUrl.from(Something.getDescriptor())
        );
        InboxMessage newerMessage = toDeliver(
                Timestamps.fromSeconds(100001L),
                TypeUrl.from(Something.getDescriptor())
        );
        InboxMessage newestMessage = toDeliver(
                Timestamps.fromSeconds(100002L),
                TypeUrl.from(Something.getDescriptor())
        );
        client1.writeMessages(
                olderMessage.shardIndex(),
                ImmutableList.of(olderMessage, newestMessage, newerMessage)
        );

        Optional<InboxMessage> actual =
                client2.newestMessageToDeliver(olderMessage.shardIndex());
        Truth8.assertThat(actual)
              .hasValue(newestMessage);
    }
}
