/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import com.google.common.testing.NullPointerTester;
import io.spine.message.delivery.client.given.RunCountingOperationWithResult;
import io.spine.message.delivery.client.given.RunCountingVoidOperation;
import io.spine.message.delivery.client.given.ThrowingOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.message.delivery.client.given.RunCountingOperationWithResult.*;
import static io.spine.message.delivery.client.given.RunCountingVoidOperation.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("`Propagate` error handling strategy should")
final class PropagateTest {

    private RequestExecutionStrategy strategy;

    @BeforeEach
    void setup() {
        strategy = new Propagate();
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
    @DisplayName("throw `StrategyFailedException` if `VoidOperation` failed.")
    void throwOnVoidOperationFailure() {
        assertThrows(ExecutionFailedException.class,
                     () -> strategy.runWithStrategy(new ThrowingOperation())
        );
    }

    @Test
    @DisplayName("throw `StrategyFailedException` if `OperationWithResult` failed.")
    void throwOnOperationWithResultFailure() {
        assertThrows(ExecutionFailedException.class, () -> strategy.runWithStrategy(() -> {
            throw new RuntimeException("For testing");
        }));
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
