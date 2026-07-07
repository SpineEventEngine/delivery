/*
 * Copyright 2026, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.delivery.admin.given;

import com.google.common.truth.Truth8;
import com.google.common.truth.extensions.proto.IterableOfProtosFluentAssertion;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Message;
import io.spine.grpc.MemoizingObserver;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

/**
 * A utility for starting an assertion chains with {@code MemoizingObserver}s and {@code Future}s.
 */
public final class SubscriptionAssertions {

    private static final int WAIT_SECONDS = 2;

    private SubscriptionAssertions() {
    }

    /**
     * Asserts that the given observer has no error.
     */
    public static <T> void assertHasNoError(MemoizingObserver<T> observer) {
        Truth8.assertThat(Optional.ofNullable(observer.getError()))
              .isEmpty();
    }

    /**
     * Starts an assertion chain for updates list stored in the given {@code observer}.
     */
    public static <T extends Message>
    IterableOfProtosFluentAssertion<T> assertUpdatesIn(MemoizingObserver<T> observer) {
        return assertThat(observer.responses()).comparingExpectedFieldsOnly();
    }

    /**
     * Asserts that the given {@code future} will be resolved in a {@linkplain #WAIT_SECONDS}
     * timeout with the message equals to the {@code expected} one.
     *
     * @return the message returned by the given {@code future}.
     */
    @CanIgnoreReturnValue
    public static <T extends Message> T assertContains(Future<T> future, T expected) {
        T message;
        try {
            message = future.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new IllegalStateException(e);
        }
        assertThat(message)
                .comparingExpectedFieldsOnly()
                .isEqualTo(expected);
        return message;
    }
}
