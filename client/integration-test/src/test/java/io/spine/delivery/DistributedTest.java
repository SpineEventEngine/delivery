/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery;

import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.HostConfig;
import com.google.common.collect.ImmutableList;
import io.spine.delivery.client.SimpleDeliveryClient;
import io.spine.delivery.client.given.ExecutionCountingStrategy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.provider.Arguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * An abstract base for tests that utilize the distribution feature of the Hazelcast-based
 * Liquor storage.
 */
abstract class DistributedTest {

    /**
     * Determines a number of iterations that each test will be executed with randomly
     * chosen clients.
     */
    private static final int EACH_TEST_ITERATIONS_COUNT = 10;

    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedTest.class);

    private static final Network network = Network.newNetwork();

    /**
     * Docker image name of the Liquor server.
     */
    private static final DockerImageName IMAGE_NAME = DockerImageName
            .parse("gcr.io/spine-dev/simple-message-delivery-server:latest");

    private static final ImmutableList<GenericContainer<?>> servers = ImmutableList.of(
            newLiquorContainer("=[1]="),
            newLiquorContainer("=[2]="),
            newLiquorContainer("=[3]=")
    );

    @SuppressWarnings("UnsecureRandomNumberGeneration") // Used for a non security purpose.
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
        servers.forEach(server -> {
            server.start();
            addDelay(server);
        });
    }

    @AfterEach
    void stopServer() {
        servers.forEach(GenericContainer::stop);
    }

    @AfterAll
    static void releaseResources() {
        servers.forEach(GenericContainer::close);
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
     * Creates a new {@code SimpleDeliveryClient} connecting to the given
     * started {@code container}.
     */
    private static SimpleDeliveryClient clientFor(GenericContainer<?> container) {
        return SimpleDeliveryClient.create(
                container.getHost(),
                container.getFirstMappedPort(),
                new ExecutionCountingStrategy()
        );
    }

    /**
     * Obtains a {@code SimpleDeliveryClient} connected to a one of the created servers.
     *
     * <p>This method doesn't create a new connection, it uses one of already created clients,
     * and randomly chooses the client to use.
     */
    private static SimpleDeliveryClient randomClient() {
        return clientFor(servers.get(random.nextInt(servers.size())));
    }

    /**
     * Returns a {@code Stream} of {@code Arguments} that contains 2 randomly
     * chosen {@code SimpleDeliveryClient}s.
     */
    protected static Stream<Arguments> clients() {
        return Stream.generate(() -> Arguments.of(randomClientSupplier(), randomClientSupplier()))
                     .limit(EACH_TEST_ITERATIONS_COUNT);
    }

    /**
     * Returns a {@code Supplier} that will be returning a new randomly chosen
     * {@code SimpleDeliveryClient} on each call.
     */
    private static Supplier<SimpleDeliveryClient> randomClientSupplier() {
        return DistributedTest::randomClient;
    }
}
