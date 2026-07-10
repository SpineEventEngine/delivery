/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.admin.security;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.AuthenticationProvider;
import io.micronaut.security.authentication.AuthenticationRequest;
import io.micronaut.security.authentication.AuthenticationResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.FluxSink.OverflowStrategy;

import static io.micronaut.security.authentication.AuthenticationResponse.exception;
import static io.micronaut.security.authentication.AuthenticationResponse.success;

/**
 * Authenticates users with {@code HTTP Basic Auth} method.
 */
@Singleton
final class HttpBasicAuthProvider implements AuthenticationProvider {

    private final HttpBasicAuthCredentials valid;

    @Inject
    HttpBasicAuthProvider(HttpBasicAuthCredentials valid) {
        this.valid = valid;
    }

    @Override
    public Publisher<AuthenticationResponse>
    authenticate(@Nullable HttpRequest<?> request, AuthenticationRequest<?, ?> authRequest) {
        return Flux.create(emitter -> authenticate(authRequest, emitter), OverflowStrategy.ERROR);
    }

    /**
     * Authenticates the user from the given {@code auth} request using provided during
     * construction credentials and emits the result using the given {@code emitter}.
     */
    private void
    authenticate(AuthenticationRequest<?, ?> auth, FluxSink<AuthenticationResponse> emitter) {
        Object identity = auth.getIdentity();
        Object secret = auth.getSecret();
        if (identity.equals(valid.username()) && secret.equals(valid.password())) {
            emitter.next(success((String) identity));
            emitter.complete();
        } else {
            emitter.error(exception());
        }
    }
}
