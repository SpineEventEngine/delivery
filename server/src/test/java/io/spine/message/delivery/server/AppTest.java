/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import io.spine.client.Client;
import io.spine.server.ServerEnvironment;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.time.Duration;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;

@Isolated
@DisplayName("`App` should")
final class AppTest {

    private final App app = new App();

    @AfterAll
    static void afterAll() {
        ServerEnvironment.instance().reset();
    }

    @Test
    @DisplayName("start the gRPC server")
    void startServer() {
        var appThread = new Thread(app::initAndStart);
        appThread.start();
        sleepUninterruptibly(Duration.ofSeconds(3)); // allow the server to start.

        var client = Client
                .connectTo(App.HOST, App.PORT)
                .build();
        assertThat(client.isOpen())
                .isTrue();
        app.grpc()
           .shutdownNowAndWait();
    }
}
