/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.grpc;

import com.google.common.collect.Maps;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.stub.StreamObserver;

import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Exposes the server and services health statuses.
 */
public final class HealthService extends HealthGrpc.HealthImplBase
        implements NamedHealthAwareService {

    /**
     * The "default" service name as required by the service spec.
     *
     * <p>The default service is meant to provide the overall server health status.
     */
    private static final String DEFAULT_SERVICE_NAME = "";
    private final Map<String, NamedHealthAwareService> services;
    private boolean healthy = true;

    public HealthService() {
        super();
        this.services = Maps.newConcurrentMap();
        register(() -> DEFAULT_SERVICE_NAME);
    }

    /**
     * Registers the {@code service} for tracking its health.
     */
    public HealthService register(NamedHealthAwareService service) {
        checkNotNull(service);
        services.put(service.name(), service);
        return this;
    }

    @Override
    public void check(HealthCheckRequest request,
                      StreamObserver<HealthCheckResponse> responseObserver) {
        String serviceToCheck = request.getService();
        if (isNullOrEmpty(serviceToCheck)) {
            checkAllHealthy(responseObserver);
            return;
        }
        checkHealthy(responseObserver, serviceToCheck);
    }

    private void
    checkHealthy(StreamObserver<HealthCheckResponse> responseObserver, String serviceToCheck) {
        if (healthy(serviceToCheck)) {
            responseObserver.onNext(nok());
        } else {
            responseObserver.onNext(ok());
        }
        responseObserver.onCompleted();
    }

    private void checkAllHealthy(StreamObserver<HealthCheckResponse> responseObserver) {
        if (allHealthy()) {
            responseObserver.onNext(ok());
        } else {
            responseObserver.onNext(nok());
        }
        responseObserver.onCompleted();
    }

    private static HealthCheckResponse ok() {
        return HealthCheckResponse.newBuilder()
                .setStatus(HealthCheckResponse.ServingStatus.SERVING)
                .buildPartial();
    }

    private static HealthCheckResponse nok() {
        return HealthCheckResponse.newBuilder()
                .setStatus(HealthCheckResponse.ServingStatus.NOT_SERVING)
                .buildPartial();
    }

    private boolean healthy(String serviceName) {
        NamedHealthAwareService service = services.get(serviceName);
        return service != null && service.healthy();
    }

    private boolean allHealthy() {
        for (NamedHealthAwareService service : services.values()) {
            if (!service.healthy()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean healthy() {
        return healthy;
    }

    @Override
    public void healthy(boolean value) {
        this.healthy = value;
    }

    @Override
    public String name() {
        return HealthGrpc.SERVICE_NAME;
    }

    /**
     * Marks all registered services as non-healthy.
     */
    public void markNonHealthy() {
        for (NamedHealthAwareService service : services.values()) {
            service.healthy(false);
        }
    }
}
