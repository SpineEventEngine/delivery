/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.spine.delivery;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.util.Timestamps;
import io.spine.delivery.client.DeliveryClient;
import io.spine.test.delivery.Something;
import io.spine.type.TypeUrl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Supplier;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.delivery.TestEnv.SHARD;
import static io.spine.delivery.TestEnv.WORKER;
import static io.spine.delivery.TestEnv.generate;
import static io.spine.delivery.TestEnv.newMessage;
import static io.spine.delivery.given.TestInboxMessages.toDeliver;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Tag("integration")
@DisplayName("Distributed Delivery servers should")
public class ConsistencyTest extends DistributedTest {

    @ParameterizedTest
    @MethodSource("clients")
    @DisplayName("pick up on one node and release on another")
    void pickUpAndRelease(Supplier<DeliveryClient> first,
                          Supplier<DeliveryClient> second) {
        var client1 = first.get();
        var client2 = second.get();

        var outcome = client1.pickUpShard(SHARD, WORKER);
        assertThat(outcome.hasSession())
                .isTrue();
        assertDoesNotThrow(() -> client2.releaseShard(SHARD, WORKER));
    }

    @ParameterizedTest
    @MethodSource("clients")
    @DisplayName("do not pick up on another node if already picked up")
    void doesNotPickUpShard(Supplier<DeliveryClient> first,
                            Supplier<DeliveryClient> second) {
        var client1 = first.get();
        var client2 = second.get();

        var firstAttempt = client1.pickUpShard(SHARD, WORKER);
        assertThat(firstAttempt.hasSession())
                .isTrue();
        var secondAttempt = client2.pickUpShard(SHARD, WORKER);
        assertThat(secondAttempt.hasAlreadyPicked())
                .isTrue();
    }

    @ParameterizedTest
    @MethodSource("clients")
    @DisplayName("pick up, release, and allow a new pick up")
    void allowToPickUpReleasedShard(Supplier<DeliveryClient> first,
                                    Supplier<DeliveryClient> second) {
        var client1 = first.get();
        var client2 = second.get();

        var firstAttempt = client1.pickUpShard(SHARD, WORKER);
        assertThat(firstAttempt.hasSession())
                .isTrue();
        var secondAttempt = client2.pickUpShard(SHARD, WORKER);
        assertThat(secondAttempt.hasAlreadyPicked())
                .isTrue();
        assertDoesNotThrow(() -> client2.releaseShard(SHARD, WORKER));
        var thirdAttempt = client1.pickUpShard(SHARD, WORKER);
        assertThat(thirdAttempt.hasSession())
                .isTrue();
    }

    @ParameterizedTest
    @MethodSource("clients")
    @DisplayName("write a message to the Inbox")
    void writeMessage(Supplier<DeliveryClient> first,
                      Supplier<DeliveryClient> second) {
        var client1 = first.get();
        var client2 = second.get();

        var message = newMessage();
        client1.writeMessage(message);

        var readMessage = client2.find(message.getId());
        assertThat(readMessage)
                .isPresent();
    }

    @ParameterizedTest
    @MethodSource("clients")
    @DisplayName("write messages to the Inbox in bulk")
    void writeMessages(Supplier<DeliveryClient> first,
                       Supplier<DeliveryClient> second) {
        var client1 = first.get();
        var client2 = second.get();

        var firstMessage = newMessage();
        var secondMessage = newMessage();
        var shard = firstMessage.shardIndex();
        client1.writeMessages(
                shard, ImmutableList.of(firstMessage, secondMessage)
        );
        var writtenMessages = client2.readAll(shard, 10);
        assertThat(writtenMessages.size())
                .isEqualTo(2);
    }

    @ParameterizedTest
    @MethodSource("clients")
    @DisplayName("read messages in pages")
    void readPages(Supplier<DeliveryClient> first,
                   Supplier<DeliveryClient> second) {
        var client1 = first.get();
        var client2 = second.get();

        var messages = generate(30);
        var shard = messages.get(0)
                            .shardIndex();
        client1.writeMessages(shard, messages);

        var pageSize = 10;
        var writtenMessages = client2.readAll(shard, pageSize);
        assertThat(writtenMessages.size())
                .isEqualTo(pageSize);
        assertThat(writtenMessages.next())
                .isPresent();
        assertThat(writtenMessages.next())
                .isPresent();
        assertThat(writtenMessages.next())
                .isEmpty();
    }

    @ParameterizedTest
    @MethodSource("clients")
    @DisplayName("read newest message to deliver")
    void readNewest(Supplier<DeliveryClient> first,
                    Supplier<DeliveryClient> second) {
        var client1 = first.get();
        var client2 = second.get();

        var olderMessage = toDeliver(
                Timestamps.fromSeconds(100000L),
                TypeUrl.from(Something.getDescriptor())
        );
        var newerMessage = toDeliver(
                Timestamps.fromSeconds(100001L),
                TypeUrl.from(Something.getDescriptor())
        );
        var newestMessage = toDeliver(
                Timestamps.fromSeconds(100002L),
                TypeUrl.from(Something.getDescriptor())
        );
        client1.writeMessages(
                olderMessage.shardIndex(),
                ImmutableList.of(olderMessage, newestMessage, newerMessage)
        );

        var actual =
                client2.newestMessageToDeliver(olderMessage.shardIndex());
        assertThat(actual)
                .hasValue(newestMessage);
    }
}
