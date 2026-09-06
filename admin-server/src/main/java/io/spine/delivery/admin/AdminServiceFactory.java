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

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.micronaut.context.annotation.Factory;
import io.spine.delivery.admin.grpc.AdminServiceGrpc;
import jakarta.inject.Singleton;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Factory for creating {@link ManagedChannel}s connected to the Delivery server running on the
 * same instance.
 *
 * <p>The factory assumes that the Delivery server is running on the same instance, so it
 * tries to get the port of the Delivery server from the {@code PORT} environment variable that
 * is used to configure the Delivery server's port. If the {@code PORT} is not set, it
 * defaults to {@code 8484} that is the default port for the Delivery server.
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
     * Creates a new {@code AdminServiceBlockingStub} connected to the Delivery server running on
     * the same instance.
     */
    @Singleton
    public AdminServiceGrpc.AdminServiceBlockingStub blockingAdminService() {
        return AdminServiceGrpc.newBlockingStub(channel);
    }

    /**
     * Returns the port from the {@code PORT} environment variable, or the default
     * {@code 8484} if the variable is not set.
     */
    private static int port() {
        @SuppressWarnings("CallToSystemGetenv")
        var port = System.getenv("PORT");
        if (isNullOrEmpty(port)) {
            return DEFAULT_PORT;
        }
        return Integer.parseInt(port);
    }
}
