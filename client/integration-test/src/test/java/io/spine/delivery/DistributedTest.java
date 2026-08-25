/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery;

import com.github.dockerjava.api.model.Capability;
import com.google.common.collect.ImmutableList;
import io.spine.delivery.client.SimpleDeliveryClient;
import io.spine.delivery.client.given.ExecutionCountingStrategy;
import io.spine.delivery.given.DeliveryImage;
import io.spine.delivery.given.RequiresDeliveryImage;
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
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * An abstract base for tests that utilize the distribution feature of the Hazelcast-based
 * Delivery storage.
 */
@RequiresDeliveryImage
abstract class DistributedTest {

    /**
     * Determines a number of iterations that each test will be executed with randomly
     * chosen clients.
     */
    private static final int EACH_TEST_ITERATIONS_COUNT = 10;

    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedTest.class);

    private static final Network network = Network.newNetwork();

    /**
     * The image the containers of this test actually run.
     *
     * <p>It adds the {@code iproute2} package on top of the Delivery server image.
     * That package provides {@code tc}, which {@link #addDelay(GenericContainer)} needs
     * to emulate an unstable network. The server image does not ship it: Jib builds that
     * image on its default Temurin JRE base, which carries no network tooling.
     *
     * <p>The package is added here, rather than to the server image itself, so that
     * the published image stays free of network administration tools.
     */
    @SuppressWarnings("HardcodedLineSeparator") /* Dockerfile lines are LF-separated
        whatever the host OS is. */
    private static final ImageFromDockerfile TEST_IMAGE =
            new ImageFromDockerfile("delivery-server-netem:test", false)
                    .withFileFromString("Dockerfile", String.join(
                            "\n",
                            "FROM " + DeliveryImage.NAME,
                            "RUN apt-get update"
                                    + " && apt-get install -y --no-install-recommends iproute2"
                                    + " && rm -rf /var/lib/apt/lists/*"
                    ));

    private static final ImmutableList<GenericContainer<?>> servers = ImmutableList.of(
            newDeliveryContainer("=[1]="),
            newDeliveryContainer("=[2]="),
            newDeliveryContainer("=[3]=")
    );

    /**
     * The clients created for the current test, closed by {@link #stopServer()}.
     *
     * <p>Each client owns a {@code ManagedChannel}; without closing them, every
     * channel would leak, and gRPC would report each one collected by the GC
     * as an orphan.
     */
    private static final List<SimpleDeliveryClient> clients = new ArrayList<>();

    @SuppressWarnings("UnsecureRandomNumberGeneration") // Used for a non-security purpose.
    private static final Random random = new Random();

    /**
     * Creates and configures a new {@code GenericContainer}.
     */
    @SuppressWarnings("resource") // The container is closed in the `@AfterAll` hook.
    private static GenericContainer<?> newDeliveryContainer(String logOutputPrefix) {
        return new GenericContainer<>(TEST_IMAGE)
                .withExposedPorts(8484)
                .withLogConsumer(new Slf4jLogConsumer(LOGGER).withPrefix(logOutputPrefix))
                .withNetwork(network)
                .withEnv("USE_HAZELCAST", "true")
                .withCreateContainerCmdModifier(m -> {
                    var config = m.getHostConfig()
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
        clients.forEach(SimpleDeliveryClient::close);
        clients.clear();
        servers.forEach(GenericContainer::stop);
    }

    @AfterAll
    static void releaseResources() {
        servers.forEach(GenericContainer::close);
    }

    /**
     * Uses the {@code tc} command to configure delay and loss on the side of the container
     * to emulate an unstable network.
     */
    private static void addDelay(GenericContainer<?> deliveryContainer) {
        executeInContainer(deliveryContainer, "tc qdisc add dev eth0 root netem delay 100ms 100ms");
        executeInContainer(deliveryContainer, "tc qdisc add dev lo root netem delay 100ms 100ms");
        executeInContainer(deliveryContainer, "tc qdisc change dev eth0 root netem loss 20% 50%");
        executeInContainer(deliveryContainer, "tc qdisc change dev lo root netem loss 20% 50%");
        executeInContainer(deliveryContainer, "tc qdisc list");
    }

    /**
     * Executes the given {@code command} on the given {@code deliveryContainer} and prints
     * the result.
     *
     * <p>The container has to be started before calling this method.
     */
    private static void executeInContainer(GenericContainer<?> deliveryContainer, String command) {
        Container.ExecResult execResult;
        try {
            execResult = deliveryContainer
                    .execInContainer("/bin/bash", "-c", command);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (LOGGER.isDebugEnabled()) {
            var format = String.format(
                    "= = = Exec finished with %n" +
                            " = code: `{}`,%n" +
                            " = stdout: %n" +
                            "{},%n" +
                            " = stderr: %n" +
                            "{}%n"
            );
            LOGGER.debug(format,
                         execResult.getExitCode(), execResult.getStdout(), execResult.getStderr());
        }
        if (execResult.getExitCode() != 0) {
            onShapingFailure(command, execResult);
        }
    }

    /**
     * Fails the test because a traffic-shaping command did not succeed inside the container.
     *
     * <p>Without shaping, the containers talk over a healthy network, and the suite silently
     * stops testing the very thing it exists to test — an unstable one. Failing here prevents
     * such a run from passing under false pretences.
     *
     * <p>{@code @RequiresDeliveryImage} already skips this suite where the server image is
     * absent, so reaching this method means the environment does have the image but cannot
     * shape traffic. The likely causes are the image missing the {@code iproute2} package
     * (see {@link #TEST_IMAGE}), the container not being granted the {@code NET_ADMIN}
     * capability, or the kernel providing no {@code sch_netem} module.
     */
    private static void onShapingFailure(String command, Container.ExecResult result) {
        throw newIllegalStateException(
                "Unable to emulate an unstable network." +
                        " The command `%s` finished with the code `%s`." +
                        " Stdout: `%s`. Stderr: `%s`.",
                command, result.getExitCode(), result.getStdout(), result.getStderr()
        );
    }

    /**
     * Creates a new {@code SimpleDeliveryClient} connecting to the given
     * started {@code container}.
     */
    private static SimpleDeliveryClient clientFor(GenericContainer<?> container) {
        var client = SimpleDeliveryClient.create(
                container.getHost(),
                container.getFirstMappedPort(),
                new ExecutionCountingStrategy()
        );
        clients.add(client);
        return client;
    }

    /**
     * Obtains a {@code SimpleDeliveryClient} connected to one of the created servers.
     *
     * <p>This method doesn't create a new connection; it uses one of the already created clients,
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
