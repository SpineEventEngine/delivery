/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.admin.security;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

import java.util.Optional;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Creates {@code HttpBasicAuthCredentials} that the application was preconfigured to accept.
 *
 * <p>Valid credentials should be set in {@code ADMIN_USERNAME} and {@code ADMIN_PASSWORD}
 * environment variables.
 */
@Factory
final class HttpBasicAuthCredentialsFactory {

    private static final String A_USERNAME = "ADMIN_USERNAME";

    private static final String A_PASSWORD = "ADMIN_PASSWORD";

    private static final String ADMIN = "admin";

    @Singleton
    public HttpBasicAuthCredentials validCredentials() {
        return fromEnvVars().orElse(new HttpBasicAuthCredentials(ADMIN, ADMIN));
    }

    /**
     * Reads and returns valid credentials from environment variables or empty
     * if variables are not set.
     */
    @SuppressWarnings("CallToSystemGetenv")
    private static Optional<HttpBasicAuthCredentials> fromEnvVars() {
        String username = System.getenv(A_USERNAME);
        String password = System.getenv(A_PASSWORD);
        if (isNullOrEmpty(username) || isNullOrEmpty(password)) {
            return Optional.empty();
        } else {
            return Optional.of(new HttpBasicAuthCredentials(username, password));
        }
    }
}
