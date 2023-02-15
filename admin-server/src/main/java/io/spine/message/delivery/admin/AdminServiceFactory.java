/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.admin;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.micronaut.context.annotation.Factory;
import io.spine.message.delivery.admin.grpc.AdminServiceGrpc;
import io.spine.message.delivery.admin.grpc.AdminServiceGrpc.AdminServiceFutureStub;
import jakarta.inject.Singleton;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Factory for creating {@link ManagedChannel}s connected to the Liquor server running on the
 * same instance.
 *
 * <p>The factory assumes that the Liquor server is running on the same instance, so it
 * tries to get the port of the Liquor server from the {@code PORT} environment variable that
 * is used to configure Liquor server's port. If the {@code PORT} is not set it
 * defaults to {@code 8484} which is default port for the Liquor server.
 */
@Factory
final class AdminServiceFactory {

    private static final String LOCALHOST = "127.0.0.1";

    private static final int DEFAULT_PORT = 8484;

    private final ManagedChannel channel = ManagedChannelBuilder
            .forAddress(LOCALHOST, port())
            .usePlaintext()
            .build();

    /**
     * Creates a new {@code AdminServiceFutureStub} connected to the Liquor server running on
     * the same instance.
     */
    @Singleton
    public AdminServiceFutureStub adminService() {
        return AdminServiceGrpc.newFutureStub(channel);
    }

    /**
     * Returns port from the {@code PORT} environment variable or default {@code 8484} if not set.
     */
    private static int port() {
        @SuppressWarnings("CallToSystemGetenv")
        String port = System.getenv("PORT");
        if (isNullOrEmpty(port)) {
            return DEFAULT_PORT;
        }
        return Integer.parseInt(port);
    }
}
