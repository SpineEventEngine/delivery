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

package io.spine.delivery.admin.given;

import com.google.common.truth.Truth;
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
 * A utility for starting an assertion chain with {@code MemoizingObserver}s and {@code Future}s.
 */
public final class SubscriptionAssertions {

    private static final int WAIT_SECONDS = 2;

    private SubscriptionAssertions() {
    }

    /**
     * Asserts that the given observer has no error.
     */
    public static <T> void assertHasNoError(MemoizingObserver<T> observer) {
        Truth.assertThat(Optional.ofNullable(observer.getError()))
             .isEmpty();
    }

    /**
     * Starts an assertion chain for the update list stored in the given {@code observer}.
     */
    public static <T extends Message>
    IterableOfProtosFluentAssertion<T> assertUpdatesIn(MemoizingObserver<T> observer) {
        return assertThat(observer.responses()).comparingExpectedFieldsOnly();
    }

    /**
     * Asserts that the given {@code future} will be resolved in a {@linkplain #WAIT_SECONDS}
     * timeout with the message equal to the {@code expected} one.
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
