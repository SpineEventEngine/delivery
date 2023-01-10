/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import com.google.common.testing.NullPointerTester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.message.delivery.client.RunCountingOperationWithResult.newRunCountingOperationWithResult;
import static io.spine.message.delivery.client.RunCountingVoidOperation.newRunCountingVoidOperation;
import static io.spine.message.delivery.client.RunCountingVoidOperation.throwUntil;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DisplayName("RetryImmediately error handling strategy should")
final class RetryImmediatelyTest {

    private RequestExecutionStrategy strategy;

    @BeforeEach
    void setup() {
        strategy = RetryImmediately.times(3);
    }

    @Test
    @DisplayName("do not allow `null`s")
    void beNpeSafe() {
        NullPointerTester tester = new NullPointerTester();
        tester.testAllPublicInstanceMethods(strategy);
        tester.testAllPublicConstructors(strategy.getClass());
        tester.testAllPublicStaticMethods(strategy.getClass());
    }

    @Test
    @DisplayName("execute `VoidOperation`")
    void executeVoidOperation() {
        RunCountingVoidOperation operation = newRunCountingVoidOperation();
        strategy.runWithStrategy(operation);

        assertThat(operation.runCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("execute `OperationWithResult`")
    void executeOperationWithResult() {
        RunCountingOperationWithResult operation = newRunCountingOperationWithResult();
        strategy.runWithStrategy(operation);

        assertThat(operation.runCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("retry `VoidOperation` on failure.")
    void retryVoidOperationOnFailure() {
        RunCountingVoidOperation operation = throwUntil(2);
        strategy.runWithStrategy(operation);

        assertThat(operation.runCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("retry `OperationWithResult` on failure.")
    void retryOperationWithResultOnFailure() {
        RunCountingOperationWithResult operation = RunCountingOperationWithResult.throwUntil(2);
        strategy.runWithStrategy(operation);

        assertThat(operation.runCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("do not throw exceptions if `VoidOperation` succeeded.")
    void doNotThrowOnVoidOperationSuccess() {
        assertDoesNotThrow(() -> strategy.runWithStrategy(() -> {
        }));
    }

    @Test
    @DisplayName("do not throw exceptions if `OperationWithResult` succeeded.")
    void doNotThrowOnOperationWithResultSuccess() {
        assertDoesNotThrow(() -> strategy.runWithStrategy(() -> "Test"));
    }
}
