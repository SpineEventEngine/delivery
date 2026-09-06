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

package io.spine.delivery.admin;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
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
import io.spine.delivery.admin.grpc.ShardInfoList;
import io.spine.delivery.admin.security.HttpBasicAuthCredentials;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

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

    private Server grpcServer;

    private static final String username = "testUsername";

    private static final String password = "testPassword";

    @BeforeEach
    void startGrpc() throws IOException {
        grpcServer = ServerBuilder
                .forPort(8484)
                .addService(LocalAdminService.onGetShardInfo(ShardInfoSecurityTest::successEmpty))
                .build()
                .start();
    }

    @AfterEach
    void stopGrpc() throws InterruptedException {
        grpcServer.shutdown()
                  .awaitTermination();
    }

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
