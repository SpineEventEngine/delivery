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

/**
 * An abstract base for tests that utilize the distribution feature of the Hazelcast-based
 * Liquor storage.
 */
abstract class DistributedTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedTest.class);

    private static final Network network = Network.newNetwork();

    /**
     * Docker image name of the Liquor server.
     */
    private static final DockerImageName IMAGE_NAME = DockerImageName
            .parse("gcr.io/spine-dev/simple-message-delivery-server:latest");

    private static final GenericContainer<?> firstServer = newLiquorContainer("=[1]=");
    private static final GenericContainer<?> secondServer = newLiquorContainer("=[2]=");
    private static final GenericContainer<?> thirdServer = newLiquorContainer("=[3]=");

    @SuppressWarnings("UnsecureRandomNumberGeneration") // This is not a security purpose.
    private static final Random random = new Random();

    /**
     * Creates and configures a new {@code GenericContainer}.
     */
    @SuppressWarnings("resource") // Container is closed in the `@AfterAll` hook.
    private static GenericContainer<?> newLiquorContainer(String loggOutputPrefix) {
        return new GenericContainer<>(IMAGE_NAME)
                .withExposedPorts(8484)
                .withLogConsumer(new Slf4jLogConsumer(LOGGER).withPrefix(loggOutputPrefix))
                .withNetwork(network)
                .withEnv("USE_HAZELCAST", "true")
                .withCreateContainerCmdModifier(m -> {
                    HostConfig config = m.getHostConfig()
                                         .withCapAdd(Capability.NET_ADMIN);
                    m.withHostConfig(config);
                });
    }

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

    @AfterAll
    static void releaseResources() {
        firstServer.close();
        secondServer.close();
        thirdServer.close();
    }

    /**
     * Uses the {@code tc} command to configure delay and loss on the side of the container
     * to emulate unstable network.
     */
    private static void addDelay(GenericContainer<?> liquorContainer) {
        executeInContainer(liquorContainer, "tc qdisc add dev eth0 root netem delay 100ms 100ms");
        executeInContainer(liquorContainer, "tc qdisc add dev lo root netem delay 100ms 100ms");
        executeInContainer(liquorContainer, "tc qdisc change dev eth0 root netem loss 20% 50%");
        executeInContainer(liquorContainer, "tc qdisc change dev lo root netem loss 20% 50%");
        executeInContainer(liquorContainer, "tc qdisc list");
    }

    /**
     * Executes the given {@code command} on the given {@code liquorContainer} and prints
     * the result.
     *
     * <p>Container have to be started before calling this method.
     */
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

    /**
     *  Creates a new {@code SimpleDeliveryClient} connecting to the given
     *  started {@code container}.
     */
    private static SimpleDeliveryClient clientFor(GenericContainer<?> container) {
        return SimpleDeliveryClient.create(
                container.getHost(),
                container.getFirstMappedPort(),
                new ExecutionCountingStrategy()
        );
    }

    /**
     * Gets a {@code SimpleDeliveryClient} connected to a one of the created servers.
     *
     * <p>The server is chosen randomly.
     */
    private static SimpleDeliveryClient getRandomClient() {
        SimpleDeliveryClient[] client = {
                clientFor(firstServer),
                clientFor(secondServer),
                clientFor(thirdServer)
        };
        return client[random.nextInt(client.length)];
    }

    /**
     * Returns a {@code Stream} of {@code Arguments} that contains 2 randomly
     * chosen {@code SimpleDeliveryClient}s.
     */
    protected static Stream<Arguments> clients() {
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
}
