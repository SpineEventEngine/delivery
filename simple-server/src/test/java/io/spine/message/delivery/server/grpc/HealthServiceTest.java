/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.grpc;

import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.spine.message.delivery.admin.grpc.AdminServiceGrpc;
import io.spine.message.delivery.InboxServiceGrpc;
import io.spine.message.delivery.ShardServiceGrpc;
import io.spine.message.delivery.server.WithApp;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

@DisplayName("`HealthService` should")
final class HealthServiceTest extends WithApp {

    private HealthGrpc.@MonotonicNonNull HealthBlockingStub healthService;

    @BeforeEach
    void initService() {
        healthService = HealthGrpc.newBlockingStub(serverChannel());
    }

    @Test
    @DisplayName("check overall server health")
    void checkServerHealth() {
        var expected = HealthCheckResponse.newBuilder()
                .setStatus(HealthCheckResponse.ServingStatus.SERVING)
                .buildPartial();
        var message = HealthCheckRequest.newBuilder()
                .buildPartial();
        var response = healthService.check(message);
        assertThat(response)
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("services")
    @DisplayName("check health of a particular service")
    void checkServiceHealth(String serviceName) {
        var expected = HealthCheckResponse.newBuilder()
                .setStatus(HealthCheckResponse.ServingStatus.SERVING)
                .buildPartial();
        var message = HealthCheckRequest.newBuilder()
                .setService(serviceName)
                .buildPartial();
        var response = healthService.check(message);
        assertThat(response)
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("ensure service status may change")
    void ensureStatusChane() {
        var expected = HealthCheckResponse.newBuilder()
                .setStatus(HealthCheckResponse.ServingStatus.NOT_SERVING)
                .buildPartial();
        var message = HealthCheckRequest.newBuilder()
                .buildPartial();
        app().healthService()
             .markNonHealthy();
        var response = healthService.check(message);
        assertThat(response)
                .isEqualTo(expected);
    }

    private static Stream<Arguments> services() {
        return Stream.of(
                Arguments.of(HealthGrpc.SERVICE_NAME),
                Arguments.of(InboxServiceGrpc.SERVICE_NAME),
                Arguments.of(ShardServiceGrpc.SERVICE_NAME),
                Arguments.of(AdminServiceGrpc.SERVICE_NAME)
        );
    }
}
