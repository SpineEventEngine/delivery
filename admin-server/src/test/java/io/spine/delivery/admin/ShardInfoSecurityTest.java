/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package io.spine.delivery.admin;

import com.google.common.net.InetAddresses;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.spine.delivery.admin.given.LocalAdminService;
import io.spine.delivery.admin.grpc.AdminServiceGrpc;
import io.spine.delivery.admin.grpc.AdminServiceGrpc.AdminServiceBlockingStub;
import io.spine.delivery.admin.grpc.ShardInfoList;
import io.spine.delivery.admin.security.HttpBasicAuthCredentials;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;

import static io.micronaut.http.HttpStatus.OK;
import static io.micronaut.http.HttpStatus.UNAUTHORIZED;
import static io.micronaut.http.MediaType.TEXT_JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
@DisplayName("`App` should secure the `/admin/shardInfo` endpoint and")
class ShardInfoSecurityTest {

    @Inject
    @Client("/admin/shardInfo")
    HttpClient client;

    /**
     * The loopback address that the stand-in for the Delivery server binds and its channel
     * dials.
     *
     * <p>The server binds this address, rather than all interfaces. On macOS, a server
     * listening on all interfaces can share its port with a server of another process that
     * listens on the loopback address. That other server then receives the channel's
     * connections.
     */
    private static final String LOCALHOST = "127.0.0.1";

    private static final String username = "testUsername";

    private static final String password = "testPassword";

    @AfterEach
    void refreshClient() {
        client.refresh();
    }

    @Test
    @DisplayName("allow access with correct credentials")
    void allowCorrectCredentials() {
        var request = HttpRequest
                .GET("/")
                .accept(TEXT_JSON)
                .basicAuth(username, password);
        @SuppressWarnings("resource") // The client should not be closed in the test method.
        var response = blockingClient().exchange(request, String.class);

        assertEquals(OK, response.getStatus());
    }

    @Test
    @DisplayName("reject access with invalid credentials")
    void rejectInvalidCredential() {
        var invalid = "invalid";
        var request = HttpRequest
                .GET("/")
                .accept(TEXT_JSON)
                .basicAuth(invalid, invalid);

        @SuppressWarnings("resource") // The client should not be closed in the test method.
        var thrown =
                assertThrows(HttpClientResponseException.class,
                             () -> blockingClient().exchange(request));
        assertEquals(UNAUTHORIZED, thrown.getStatus());
    }

    @Test
    @DisplayName("reject access without authentication")
    void rejectNoCredentials() {
        var request = HttpRequest
                .GET("/")
                .accept(TEXT_JSON);

        @SuppressWarnings("resource") // The client should not be closed in the test method.
        var thrown =
                assertThrows(HttpClientResponseException.class,
                             () -> blockingClient().exchange(request));
        assertEquals(UNAUTHORIZED, thrown.getStatus());
    }

    /**
     * Provides test credentials for the application context.
     */
    @Singleton
    @Replaces(HttpBasicAuthCredentials.class)
    HttpBasicAuthCredentials credentials() {
        return new HttpBasicAuthCredentials(username, password);
    }

    /**
     * Starts the Delivery server stand-in, which serves an empty list of shards.
     *
     * <p>As a bean of the application context, the server starts once for all the tests
     * of the class, at a port that the operating system assigns. Binding a fixed port
     * before each test fails on Windows, where a server binds its port exclusively:
     * a connection that the previous server accepted, lingering in {@code TIME_WAIT},
     * keeps the next server from binding the same port.
     */
    @Singleton
    @Bean(preDestroy = "shutdownNow")
    Server deliveryServer() throws IOException {
        return NettyServerBuilder
                .forAddress(new InetSocketAddress(InetAddresses.forString(LOCALHOST), 0))
                .addService(LocalAdminService.onGetShardInfo(ShardInfoSecurityTest::successEmpty))
                .build()
                .start();
    }

    /**
     * Provides a channel to the given stand-in for the Delivery server.
     */
    @Singleton
    @Bean(preDestroy = "shutdownNow")
    ManagedChannel deliveryChannel(Server deliveryServer) {
        return ManagedChannelBuilder
                .forAddress(LOCALHOST, deliveryServer.getPort())
                .usePlaintext()
                .build();
    }

    /**
     * Provides the {@code AdminServiceBlockingStub} that calls the stand-in through the given
     * channel, in place of the stub that {@link AdminServiceFactory} creates for the port of
     * the Delivery server.
     */
    @Singleton
    @Replaces(value = AdminServiceBlockingStub.class, factory = AdminServiceFactory.class)
    AdminServiceBlockingStub blockingAdminService(ManagedChannel deliveryChannel) {
        return AdminServiceGrpc.newBlockingStub(deliveryChannel);
    }

    /**
     * Returns a {@code BlockingHttpClient} for performing test requests.
     */
    private BlockingHttpClient blockingClient() {
        return this.client.toBlocking();
    }

    /**
     * Returns a {@code ShardInfoList.getDefaultInstance()} to the given {@code response} and
     * completes it.
     */
    private static void successEmpty(StreamObserver<ShardInfoList> response) {
        response.onNext(ShardInfoList.getDefaultInstance());
        response.onCompleted();
    }
}
