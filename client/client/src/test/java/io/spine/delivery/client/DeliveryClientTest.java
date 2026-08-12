/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
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
import io.spine.test.delivery.Something;
import io.spine.type.TypeUrl;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import io.spine.delivery.given.DeliveryImage;
import io.spine.delivery.given.RequiresDeliveryImage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static io.spine.base.Identifier.newUuid;
import static io.spine.delivery.given.TestInboxMessages.toDeliver;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Tests the {@link SimpleDeliveryClient} against the Delivery server running in
 * a Docker container.
 *
 * <p>Tagged as {@code integration}: the suite requires a Docker environment and
 * the access to the {@code gcr.io/spine-dev} registry hosting the server image
 * built by {@code :delivery-server-cloud-run:jib}.
 */
@Tag("integration")
@RequiresDeliveryImage
@DisplayName("`SimpleDeliveryClient` should")
final class DeliveryClientTest {

    private final GenericContainer<?> server = new GenericContainer<>(
            DeliveryImage.dockerImageName()
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
                .build();
        private final WorkerId worker = WorkerId.newBuilder()
                .setNodeId(node)
                .setValue(SimpleDeliveryClient.class.getName())
                .build();

        @Test
        @DisplayName("pick up a shard for delivery")
        void pickUpShard() {
            var outcome = client.pickUpShard(shard, worker);
            assertThat(outcome.hasSession())
                    .isTrue();
        }

        @Test
        @DisplayName("release a previously picked up shard")
        void releaseShard() {
            var outcome = client.pickUpShard(shard, worker);
            assertThat(outcome.hasSession())
                    .isTrue();
            assertDoesNotThrow(() -> client.releaseShard(shard, worker));
        }

        @Test
        @DisplayName("do not pick up a shard for delivery if one is already picked up")
        void notPickUpShard() {
            var firstAttempt = client.pickUpShard(shard, worker);
            assertThat(firstAttempt.hasSession())
                    .isTrue();
            var secondAttempt = client.pickUpShard(shard, worker);
            assertThat(secondAttempt.hasAlreadyPicked())
                    .isTrue();

            var expected = ShardAlreadyPickedUp.newBuilder()
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
            var message = newMessage();
            client.writeMessage(message);

            var readMessage = client.find(message.getId());
            assertThat(readMessage)
                    .isPresent();
        }

        @Test
        @DisplayName("write messages to the Inbox in bulk")
        void writeMessages() {
            var firstMessage = newMessage();
            var secondMessage = newMessage();
            var shard = firstMessage.shardIndex();
            client.writeMessages(
                    shard, ImmutableList.of(firstMessage, secondMessage)
            );
            var writtenMessages = client.readAll(shard, 10);
            assertThat(writtenMessages.size())
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("remove a message from the Inbox")
        void removeMessage() {
            var message = newMessage();
            client.writeMessage(message);
            client.removeMessage(message);
            var readMessage = client.find(message.getId());
            assertThat(readMessage)
                    .isEmpty();
        }

        @Test
        @DisplayName("remove messages from the Inbox in bulk")
        void removeMessages() {
            var firstMessage = newMessage();
            var secondMessage = newMessage();
            var shard = firstMessage.shardIndex();
            client.writeMessages(
                    shard, ImmutableList.of(firstMessage, secondMessage)
            );
            client.removeMessages(
                    shard, ImmutableList.of(firstMessage, secondMessage)
            );
            var writtenMessages = client.readAll(shard, 10);
            assertThat(writtenMessages.size())
                    .isEqualTo(0);
        }

        @Test
        @DisplayName("use provided `RequestExecutionStrategy` writing a message")
        void useStrategyWritingMessage() {
            var message = newMessage();
            client.writeMessage(message);

            assertThat(strategy.voidExecutions())
                    .isEqualTo(0);
            assertThat(strategy.withResultEvaluations())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("use provided `RequestExecutionStrategy` removing a message")
        void useStrategyRemovingMessage() {
            var message = newMessage();
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
            var firstMessage = newMessage();
            var secondMessage = newMessage();
            var shard = firstMessage.shardIndex();
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
            var firstMessage = newMessage();
            var secondMessage = newMessage();
            var shard = firstMessage.shardIndex();
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
            var message = newMessage();
            var readMessage = client.find(message.getId());
            assertThat(readMessage)
                    .isEmpty();
        }

        @Test
        @DisplayName("find a message in the inbox")
        void find() {
            var message = newMessage();
            client.writeMessage(message);

            var readMessage = client.find(message.getId());
            assertThat(readMessage)
                    .isPresent();
        }

        @Test
        @DisplayName("read messages in pages")
        void readPages() {
            var messages = generate(30);
            var shard = messages.get(0)
                                .shardIndex();
            client.writeMessages(shard, messages);

            var pageSize = 10;
            var writtenMessages = client.readAll(shard, pageSize);
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
            client.writeMessages(
                    olderMessage.shardIndex(),
                    ImmutableList.of(olderMessage, newestMessage, newerMessage)
            );

            var actual =
                    client.newestMessageToDeliver(olderMessage.shardIndex());
            assertThat(actual)
                    .hasValue(newestMessage);
        }

        @Test
        @DisplayName("use provided `RequestExecutionStrategy` finding a message")
        void useStrategyFinding() {
            var message = newMessage();
            client.find(message.getId());

            assertThat(strategy.voidExecutions())
                    .isEqualTo(0);
            assertThat(strategy.withResultEvaluations())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("use provided `RequestExecutionStrategy` finding multiple messages")
        void useStrategyFindingMany() {
            var messages = generate(30);
            var shard = messages.get(0)
                                .shardIndex();
            client.writeMessages(shard, messages);
            sleepUninterruptibly(1, TimeUnit.SECONDS);
            var pageSize = 10;
            client.readAll(shard, pageSize);

            assertThat(strategy.voidExecutions())
                    .isEqualTo(0);
            assertThat(strategy.withResultEvaluations())
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("use provided `RequestExecutionStrategy` finding newest messages")
        void useStrategyReadingNewest() {
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
