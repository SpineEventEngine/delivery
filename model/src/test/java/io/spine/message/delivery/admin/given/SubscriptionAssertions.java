/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.admin.given;

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

public final class SubscriptionAssertions {

    private static final int WAIT_SECONDS = 2;

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

    @CanIgnoreReturnValue
    public static <T extends Message> T assertContains(Future<T> future, T expected) {
        T message;
        try {
            message = future.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
        assertThat(message)
                .comparingExpectedFieldsOnly()
                .isEqualTo(expected);
        return message;
    }
}
