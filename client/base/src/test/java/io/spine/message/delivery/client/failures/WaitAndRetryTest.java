/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client.failures;

import com.google.common.testing.NullPointerTester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.message.delivery.client.failures.RunCountingOperationWithResult.newRunCountingOperationWithResult;
import static io.spine.message.delivery.client.failures.RunCountingVoidOperation.newRunCountingVoidOperation;
import static io.spine.message.delivery.client.failures.RunCountingVoidOperation.throwUntil;
import static java.lang.System.*;
import static java.time.Duration.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WaitAndRetry error handling strategy should")
final class WaitAndRetryTest {

    private ErrorHandlingStrategy strategy;

    @BeforeEach
    void setup() {
        strategy = WaitAndRetry.waitForSeconds(2)
                               .times(2);
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
    @DisplayName("execute `VoidOperation` without waiting")
    void executeVoidOperation() {
        RunCountingVoidOperation operation = newRunCountingVoidOperation();

        assertTimeout(ofSeconds(1), () -> strategy.runWithStrategy(operation));
        assertThat(operation.runCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("execute `OperationWithResult` without waiting")
    void executeOperationWithResult() {
        RunCountingOperationWithResult operation = newRunCountingOperationWithResult();

        assertTimeout(ofSeconds(1), () -> strategy.runWithStrategy(operation));
        assertThat(operation.runCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("retry `VoidOperation` on failure with waiting")
    void retryVoidOperationOnFailure() {
        RunCountingVoidOperation operation = throwUntil(2);

        long before = currentTimeMillis();
        strategy.runWithStrategy(operation);
        long after = currentTimeMillis();

        assertThat(after - before).isAtLeast(2000);
        assertThat(operation.runCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("retry `OperationWithResult` on failure with waiting")
    void retryOperationWithResultOnFailure() {
        RunCountingOperationWithResult operation = RunCountingOperationWithResult.throwUntil(2);

        long before = currentTimeMillis();
        strategy.runWithStrategy(operation);
        long after = currentTimeMillis();

        assertThat(after - before).isAtLeast(2000);
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
