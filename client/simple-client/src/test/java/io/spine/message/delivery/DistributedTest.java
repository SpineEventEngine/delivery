/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery;

import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.HostConfig;
import com.google.common.truth.Truth8;
import com.google.protobuf.util.Timestamps;
import io.spine.logging.Logging;
import io.spine.message.delivery.client.SimpleDeliveryClient;
import io.spine.message.delivery.client.given.ExecutionCountingStrategy;
import io.spine.server.NodeId;
import io.spine.server.delivery.DeliveryStrategy;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.Page;
import io.spine.server.delivery.PickUpOutcome;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.WorkerId;
import io.spine.test.message.delivery.client.Something;
import io.spine.type.TypeUrl;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.base.Identifier.newUuid;
import static io.spine.message.delivery.client.given.TestInboxMessages.toDeliver;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DisplayName("Distributed Liquor servers should")
public class DistributedTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedTest.class);

    private final ShardIndex shard = DeliveryStrategy.newIndex(1, 2);
    private final NodeId node = NodeId.newBuilder()
            .setValue(SimpleDeliveryClient.class.getName())
            .vBuild();
    private final WorkerId worker = WorkerId.newBuilder()
            .setNodeId(node)
            .setValue(SimpleDeliveryClient.class.getName())
            .vBuild();

    private static final Network network = Network.newNetwork();

    private static final DockerImageName IMAGE_NAME = DockerImageName
            .parse("gcr.io/spine-dev/simple-message-delivery-server:latest");
    private static final GenericContainer<?> firstServer = new GenericContainer<>(IMAGE_NAME)
            .withExposedPorts(8484)
            .withLogConsumer(new Slf4jLogConsumer(LOGGER).withPrefix("=[1]="))
            .withNetwork(network)
            .withEnv("USE_HAZELCAST", "true")
            .withCreateContainerCmdModifier(m -> {
                HostConfig config = m.getHostConfig()
                                     .withCapAdd(Capability.NET_ADMIN);
                m.withHostConfig(config);
            });

    private static final GenericContainer<?> secondServer = new GenericContainer<>(IMAGE_NAME)
            .withExposedPorts(8484)
            .withLogConsumer(new Slf4jLogConsumer(LOGGER).withPrefix("=[2]="))
            .withNetwork(network)
            .withEnv("USE_HAZELCAST", "true")
            .withCreateContainerCmdModifier(m -> {
                HostConfig config = m.getHostConfig()
                                     .withCapAdd(Capability.NET_ADMIN);
                m.withHostConfig(config);
            });

    private static final GenericContainer<?> thirdServer = new GenericContainer<>(IMAGE_NAME)
            .withExposedPorts(8484)
            .withLogConsumer(new Slf4jLogConsumer(LOGGER).withPrefix("=[3]="))
            .withNetwork(network)
            .withEnv("USE_HAZELCAST", "true")
            .withCreateContainerCmdModifier(m -> {
                HostConfig config = m.getHostConfig()
                                     .withCapAdd(Capability.NET_ADMIN);
                m.withHostConfig(config);
            });

    private static final Random random = new Random();

    @BeforeEach
    void connectClient() {
        firstServer.start();
        addDelay(firstServer);
        secondServer.start();
        addDelay(secondServer);
        thirdServer.start();
        addDelay(thirdServer);
    }

    @AfterEach
    void stopServer() {
        firstServer.stop();
        secondServer.stop();
        thirdServer.stop();
    }

    private static void addDelay(GenericContainer<?> liquorContainer) {
        executeInContainer(liquorContainer, "tc qdisc add dev eth0 root netem delay 100ms 100ms");
        executeInContainer(liquorContainer, "tc qdisc add dev lo root netem delay 100ms 100ms");
        executeInContainer(liquorContainer, "tc qdisc change dev eth0 root netem loss 20% 50%");
        executeInContainer(liquorContainer, "tc qdisc change dev lo root netem loss 20% 50%");
        executeInContainer(liquorContainer, "tc qdisc list");
    }

    private static void executeInContainer(GenericContainer<?> liquorContainer, String command) {
        Container.ExecResult execResult;
        try {
            execResult = liquorContainer
                    .execInContainer("/bin/bash", "-c", command);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        LOGGER.info("= = = Exec finished with\n = code: `{}`\n, = stdout: \n{}\n, = stderr: \n{}\n",
                    execResult.getExitCode(), execResult.getStdout(), execResult.getStderr());
    }

    private static SimpleDeliveryClient clientFor(GenericContainer<?> container) {
        return SimpleDeliveryClient.create(container.getHost(), container.getFirstMappedPort(),
                                           new ExecutionCountingStrategy());
    }

    private static SimpleDeliveryClient getRandomClient() {
        SimpleDeliveryClient[] client = {
                clientFor(firstServer),
                clientFor(secondServer),
                clientFor(thirdServer)
        };
        return client[random.nextInt(client.length)];
    }

    private static Stream<Arguments> clients() {
        return Stream.of(
                Arguments.of((Supplier<SimpleDeliveryClient>) DistributedTest::getRandomClient,
                             (Supplier<SimpleDeliveryClient>) DistributedTest::getRandomClient),
                Arguments.of((Supplier<SimpleDeliveryClient>) DistributedTest::getRandomClient,
                             (Supplier<SimpleDeliveryClient>) DistributedTest::getRandomClient),
                Arguments.of((Supplier<SimpleDeliveryClient>) DistributedTest::getRandomClient,
                             (Supplier<SimpleDeliveryClient>) DistributedTest::getRandomClient),
                Arguments.of((Supplier<SimpleDeliveryClient>) DistributedTest::getRandomClient,
                             (Supplier<SimpleDeliveryClient>) DistributedTest::getRandomClient),
                Arguments.of((Supplier<SimpleDeliveryClient>) DistributedTest::getRandomClient,
                             (Supplier<SimpleDeliveryClient>) DistributedTest::getRandomClient),
                Arguments.of((Supplier<SimpleDeliveryClient>) DistributedTest::getRandomClient,
                             (Supplier<SimpleDeliveryClient>) DistributedTest::getRandomClient),
                Arguments.of((Supplier<SimpleDeliveryClient>) DistributedTest::getRandomClient,
                             (Supplier<SimpleDeliveryClient>) DistributedTest::getRandomClient),
                Arguments.of((Supplier<SimpleDeliveryClient>) DistributedTest::getRandomClient,
                             (Supplier<SimpleDeliveryClient>) DistributedTest::getRandomClient),
                Arguments.of((Supplier<SimpleDeliveryClient>) DistributedTest::getRandomClient,
                             (Supplier<SimpleDeliveryClient>) DistributedTest::getRandomClient),
                Arguments.of((Supplier<SimpleDeliveryClient>) DistributedTest::getRandomClient,
                             (Supplier<SimpleDeliveryClient>) DistributedTest::getRandomClient)
        );
    }

    @ParameterizedTest
    @MethodSource("clients")
    @DisplayName("pick up on one node and release on another")
    void pickUpAndRelease(Supplier<SimpleDeliveryClient> first,
                          Supplier<SimpleDeliveryClient> second) {
        SimpleDeliveryClient client1 = first.get();
        SimpleDeliveryClient client2 = second.get();

        PickUpOutcome outcome = client1.pickUpShard(shard, worker);
        assertThat(outcome.hasSession())
                .isTrue();
        assertDoesNotThrow(() -> client2.releaseShard(shard, worker));
    }

    @ParameterizedTest
    @MethodSource("clients")
    @DisplayName("do not pick up on another node if already picked up")
    void doesNotPickUpShard(Supplier<SimpleDeliveryClient> first,
                            Supplier<SimpleDeliveryClient> second) {
        SimpleDeliveryClient client1 = first.get();
        SimpleDeliveryClient client2 = second.get();

        PickUpOutcome firstAttempt = client1.pickUpShard(shard, worker);
        assertThat(firstAttempt.hasSession())
                .isTrue();
        PickUpOutcome secondAttempt = client2.pickUpShard(shard, worker);
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

        PickUpOutcome firstAttempt = client1.pickUpShard(shard, worker);
        assertThat(firstAttempt.hasSession())
                .isTrue();
        PickUpOutcome secondAttempt = client2.pickUpShard(shard, worker);
        assertThat(secondAttempt.hasAlreadyPicked())
                .isTrue();
        assertDoesNotThrow(() -> client2.releaseShard(shard, worker));
        PickUpOutcome thirdAttempt = client1.pickUpShard(shard, worker);
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

    private static InboxMessage newMessage() {
        return toDeliver(newUuid(), TypeUrl.from(Something.getDescriptor()));
    }

    private static List<InboxMessage> generate(int number) {
        return IntStream
                .range(0, number)
                .mapToObj(i -> toDeliver(newUuid(), TypeUrl.from(Something.getDescriptor())))
                .collect(Collectors.toList());
    }
}
