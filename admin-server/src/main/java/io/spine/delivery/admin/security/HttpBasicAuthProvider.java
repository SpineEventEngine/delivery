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

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.AuthenticationFailureReason;
import io.micronaut.security.authentication.AuthenticationRequest;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.provider.HttpRequestAuthenticationProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static io.micronaut.security.authentication.AuthenticationResponse.failure;
import static io.micronaut.security.authentication.AuthenticationResponse.success;

/**
 * Authenticates users with the {@code HTTP Basic Auth} method.
 *
 * @param <B>
 *         the type of the HTTP request body
 */
@Singleton
final class HttpBasicAuthProvider<B> implements HttpRequestAuthenticationProvider<B> {

    private final HttpBasicAuthCredentials valid;

    @Inject
    HttpBasicAuthProvider(HttpBasicAuthCredentials valid) {
        this.valid = valid;
    }

    @Override
    public AuthenticationResponse authenticate(@Nullable HttpRequest<B> request,
                                               AuthenticationRequest<String, String> auth) {
        var identity = auth.getIdentity();
        var secret = auth.getSecret();
        // The configured credentials are known to be non-`null`, so a `null`
        // identity or secret compares as a mismatch instead of throwing.
        if (valid.username().equals(identity) && valid.password().equals(secret)) {
            return success(identity);
        }
        return failure(AuthenticationFailureReason.CREDENTIALS_DO_NOT_MATCH);
    }
}
