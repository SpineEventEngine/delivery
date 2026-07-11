/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
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
        MutableHttpRequest<Object> request = HttpRequest
                .GET("/")
                .accept(TEXT_JSON)
                .basicAuth(username, password);
        @SuppressWarnings("resource") // Client should not be closed in test method.
        HttpResponse<String> response = blockingClient().exchange(request, String.class);

        assertEquals(OK, response.getStatus());
    }

    @Test
    @DisplayName("reject access with invalid credentials")
    void rejectInvalidCredential() {
        String invalid = "invalid";
        MutableHttpRequest<Object> request = HttpRequest
                .GET("/")
                .accept(TEXT_JSON)
                .basicAuth(invalid, invalid);

        @SuppressWarnings("resource") // Client should not be closed in test method.
        HttpClientResponseException thrown =
                assertThrows(HttpClientResponseException.class,
                             () -> blockingClient().exchange(request));
        assertEquals(UNAUTHORIZED, thrown.getStatus());
    }

    @Test
    @DisplayName("reject access without authentication")
    void rejectNoCredentials() {
        MutableHttpRequest<Object> request = HttpRequest
                .GET("/")
                .accept(TEXT_JSON);

        @SuppressWarnings("resource") // Client should not be closed in test method.
        HttpClientResponseException thrown =
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
     * Returns {@code BlockingHttpClient} for performing test requests.
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
