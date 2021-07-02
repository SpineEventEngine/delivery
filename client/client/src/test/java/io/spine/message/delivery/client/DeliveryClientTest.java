/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import io.spine.server.delivery.InboxMessage;
import io.spine.test.message.delivery.server.Something;
import io.spine.type.TypeUrl;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth8.assertThat;
import static io.spine.base.Identifier.newUuid;
import static io.spine.message.delivery.client.given.TestInboxMessages.toDeliver;
import static org.testcontainers.shaded.com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;

@Testcontainers
@DisplayName("`DeliveryClient` should")
final class DeliveryClientTest {

    @Container
    private final GenericContainer<?> server = new GenericContainer<>(
            DockerImageName.parse("gcr.io/spine-dev/message-delivery-server:latest")
    ).withExposedPorts(8484);

    private @MonotonicNonNull DeliveryClient client;

    @BeforeEach
    void connectClient() {
        client = DeliveryClient.create(server.getHost(), server.getFirstMappedPort());
    }

    @Test
    @DisplayName("write a message to the Inbox")
    void writeMessage() {
        InboxMessage message = toDeliver(newUuid(), TypeUrl.from(Something.getDescriptor()));
        client.writeMessage(message);
        sleepUninterruptibly(1, TimeUnit.SECONDS);
        Optional<InboxMessage> readMessage = client.find(message.getId());
        assertThat(readMessage)
                .isPresent();
    }
}
