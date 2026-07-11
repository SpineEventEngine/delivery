/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
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
