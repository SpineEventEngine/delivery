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

package io.spine.delivery.admin.security;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

import java.util.Optional;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Creates {@code HttpBasicAuthCredentials} that the application was preconfigured to accept.
 *
 * <p>Valid credentials should be set in the {@code ADMIN_USERNAME} and {@code ADMIN_PASSWORD}
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
        var username = System.getenv(A_USERNAME);
        var password = System.getenv(A_PASSWORD);
        if (isNullOrEmpty(username) || isNullOrEmpty(password)) {
            return Optional.empty();
        } else {
            return Optional.of(new HttpBasicAuthCredentials(username, password));
        }
    }
}
