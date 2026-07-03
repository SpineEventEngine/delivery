/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.client;

import com.google.protobuf.util.Timestamps;
import io.spine.delivery.client.given.ExecutionCountingStrategy;
import io.spine.server.NodeId;
import io.spine.server.delivery.DeliveryStrategy;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.Page;
import io.spine.server.delivery.PickUpOutcome;
import io.spine.server.delivery.ShardAlreadyPickedUp;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.WorkerId;
import io.spine.test.delivery.client.Something;
import io.spine.type.TypeUrl;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static io.spine.base.Identifier.newUuid;
import static io.spine.delivery.client.given.TestInboxMessages.toDeliver;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DisplayName("`SimpleDeliveryClient` should")
final class SimpleDeliveryClientTest {

    private final GenericContainer<?> server = new GenericContainer<>(
            DockerImageName.parse("gcr.io/spine-dev/simple-message-delivery-server:latest")
    ).withExposedPorts(8484);

    private @MonotonicNonNull SimpleDeliveryClient client;

    private @MonotonicNonNull ExecutionCountingStrategy strategy;

    @BeforeEach
    void connectClient() {
        server.start();
        strategy = new ExecutionCountingStrategy();
        client = SimpleDeliveryClient
                .create(server.getHost(), server.getFirstMappedPort(), strategy);
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Nested
    @DisplayName("implement `SessionRegistryClient` interface and")
    class SessionRegistryClient {

        private final ShardIndex shard = DeliveryStrategy.newIndex(1, 2);
        private final NodeId node = NodeId.newBuilder()
                .setValue(SimpleDeliveryClient.class.getName())
                .vBuild();
        private final WorkerId worker = WorkerId.newBuilder()
                .setNodeId(node)
                .setValue(SimpleDeliveryClient.class.getName())
                .vBuild();

        @Test
        @DisplayName("pick up a shard for delivery")
        void pickUpShard() {
            PickUpOutcome outcome = client.pickUpShard(shard, worker);
            assertThat(outcome.hasSession())
                    .isTrue();
        }

        @Test
        @DisplayName("release a previously picked up shard")
        void releaseShard() {
            PickUpOutcome outcome = client.pickUpShard(shard, worker);
            assertThat(outcome.hasSession())
                    .isTrue();
            assertDoesNotThrow(() -> client.releaseShard(shard, worker));
        }

        @Test
        @DisplayName("do not pick up a shard for delivery if one is already picked up")
        void notPickUpShard() {
            PickUpOutcome firstAttempt = client.pickUpShard(shard, worker);
            assertThat(firstAttempt.hasSession())
                    .isTrue();
            PickUpOutcome secondAttempt = client.pickUpShard(shard, worker);
            assertThat(secondAttempt.hasAlreadyPicked())
                    .isTrue();

            ShardAlreadyPickedUp expected = ShardAlreadyPickedUp
                    .newBuilder()
                    .setWorker(worker)
                    .buildPartial();

            assertThat(secondAttempt.getAlreadyPicked())
                    .comparingExpectedFieldsOnly()
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("use provided `ExecutionCountingStrategy` picking up a shard.")
        void useStrategyPickingUpShard() {
            client.pickUpShard(shard, worker);

            assertThat(strategy.voidExecutions())
                    .isEqualTo(0);
            assertThat(strategy.withResultEvaluations())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("use provided `ExecutionCountingStrategy` releasing a shard.")
        void useStrategyReleasingShard() {
            client.pickUpShard(shard, worker);
            client.releaseShard(shard, worker);

            assertThat(strategy.voidExecutions())
                    .isEqualTo(0);
            assertThat(strategy.withResultEvaluations())
                    .isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("implement `InboxModifier` interface and")
    final class InboxModifier {

        @Test
        @DisplayName("write a message to the Inbox")
        void writeMessage() {
            InboxMessage message = newMessage();
            client.writeMessage(message);

            Optional<InboxMessage> readMessage = client.find(message.getId());
            assertThat(readMessage)
                    .isPresent();
        }

        @Test
        @DisplayName("write messages to the Inbox in bulk")
        void writeMessages() {
            InboxMessage firstMessage = newMessage();
            InboxMessage secondMessage = newMessage();
            ShardIndex shard = firstMessage.shardIndex();
            client.writeMessages(
                    shard, ImmutableList.of(firstMessage, secondMessage)
            );
            Page<InboxMessage> writtenMessages = client.readAll(shard, 10);
            assertThat(writtenMessages.size())
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("remove a message from the Inbox")
        void removeMessage() {
            InboxMessage message = newMessage();
            client.writeMessage(message);
            client.removeMessage(message);
            Optional<InboxMessage> readMessage = client.find(message.getId());
            assertThat(readMessage)
                    .isEmpty();
        }

        @Test
        @DisplayName("remove messages from the Inbox in bulk")
        void removeMessages() {
            InboxMessage firstMessage = newMessage();
            InboxMessage secondMessage = newMessage();
            ShardIndex shard = firstMessage.shardIndex();
            client.writeMessages(
                    shard, ImmutableList.of(firstMessage, secondMessage)
            );
            client.removeMessages(
                    shard, ImmutableList.of(firstMessage, secondMessage)
            );
            Page<InboxMessage> writtenMessages = client.readAll(shard, 10);
            assertThat(writtenMessages.size())
                    .isEqualTo(0);
        }

        @Test
        @DisplayName("use provided `RequestExecutionStrategy` writing a message")
        void useStrategyWritingMessage() {
            InboxMessage message = newMessage();
            client.writeMessage(message);

            assertThat(strategy.voidExecutions())
                    .isEqualTo(0);
            assertThat(strategy.withResultEvaluations())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("use provided `RequestExecutionStrategy` removing a message")
        void useStrategyRemovingMessage() {
            InboxMessage message = newMessage();
            client.writeMessage(message);
            client.removeMessage(message);

            assertThat(strategy.voidExecutions())
                    .isEqualTo(0);
            assertThat(strategy.withResultEvaluations())
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("use provided `RequestExecutionStrategy` writing multiple messages")
        void useStrategyWritingMessages() {
            InboxMessage firstMessage = newMessage();
            InboxMessage secondMessage = newMessage();
            ShardIndex shard = firstMessage.shardIndex();
            client.writeMessages(
                    shard, ImmutableList.of(firstMessage, secondMessage)
            );

            assertThat(strategy.voidExecutions())
                    .isEqualTo(0);
            assertThat(strategy.withResultEvaluations())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("use provided `RequestExecutionStrategy` removing multiple messages")
        void useStrategyRemovingMessages() {
            InboxMessage firstMessage = newMessage();
            InboxMessage secondMessage = newMessage();
            ShardIndex shard = firstMessage.shardIndex();
            client.writeMessages(
                    shard, ImmutableList.of(firstMessage, secondMessage)
            );
            client.removeMessages(
                    shard, ImmutableList.of(firstMessage, secondMessage)
            );

            assertThat(strategy.voidExecutions())
                    .isEqualTo(0);
            assertThat(strategy.withResultEvaluations())
                    .isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("implement `InboxReader` interface and")
    final class InboxReader {

        @Test
        @DisplayName("return empty optional if the message with the ID is not found")
        void findNone() {
            InboxMessage message = newMessage();
            Optional<InboxMessage> readMessage = client.find(message.getId());
            assertThat(readMessage)
                    .isEmpty();
        }

        @Test
        @DisplayName("find a message in the inbox")
        void find() {
            InboxMessage message = newMessage();
            client.writeMessage(message);

            Optional<InboxMessage> readMessage = client.find(message.getId());
            assertThat(readMessage)
                    .isPresent();
        }

        @Test
        @DisplayName("read messages in pages")
        void readPages() {
            List<InboxMessage> messages = generate(30);
            ShardIndex shard = messages.get(0)
                                       .shardIndex();
            client.writeMessages(shard, messages);

            int pageSize = 10;
            Page<InboxMessage> writtenMessages = client.readAll(shard, pageSize);
            assertThat(writtenMessages.size())
                    .isEqualTo(pageSize);
            assertThat(writtenMessages.next())
                    .isPresent();
            assertThat(writtenMessages.next())
                    .isPresent();
            assertThat(writtenMessages.next())
                    .isEmpty();
        }

        @Test
        @DisplayName("read newest message to deliver")
        void readNewest() {
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
            client.writeMessages(
                    olderMessage.shardIndex(),
                    ImmutableList.of(olderMessage, newestMessage, newerMessage)
            );

            Optional<InboxMessage> actual =
                    client.newestMessageToDeliver(olderMessage.shardIndex());
            assertThat(actual)
                    .hasValue(newestMessage);
        }

        @Test
        @DisplayName("use provided `RequestExecutionStrategy` finding a message")
        void useStrategyFinding() {
            InboxMessage message = newMessage();
            client.find(message.getId());

            assertThat(strategy.voidExecutions())
                    .isEqualTo(0);
            assertThat(strategy.withResultEvaluations())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("use provided `RequestExecutionStrategy` finding multiple messages")
        void useStrategyFindingMany() {
            List<InboxMessage> messages = generate(30);
            ShardIndex shard = messages.get(0)
                                       .shardIndex();
            client.writeMessages(shard, messages);
            sleepUninterruptibly(1, TimeUnit.SECONDS);
            int pageSize = 10;
            client.readAll(shard, pageSize);

            assertThat(strategy.voidExecutions())
                    .isEqualTo(0);
            assertThat(strategy.withResultEvaluations())
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("use provided `RequestExecutionStrategy` finding newest messages")
        void useStrategyReadingNewest() {
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
            client.writeMessages(
                    olderMessage.shardIndex(),
                    ImmutableList.of(olderMessage, newestMessage, newerMessage)
            );
            sleepUninterruptibly(1, TimeUnit.SECONDS);
            client.newestMessageToDeliver(olderMessage.shardIndex());

            assertThat(strategy.voidExecutions())
                    .isEqualTo(0);
            assertThat(strategy.withResultEvaluations())
                    .isEqualTo(2);
        }

        private List<InboxMessage> generate(int number) {
            return IntStream
                    .range(0, number)
                    .mapToObj(i -> toDeliver(newUuid(), TypeUrl.from(Something.getDescriptor())))
                    .collect(Collectors.toList());
        }
    }

    private static InboxMessage newMessage() {
        return toDeliver(
                newUuid(), TypeUrl.from(Something.getDescriptor())
        );
    }
}
