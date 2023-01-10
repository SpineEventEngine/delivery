/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import com.google.common.testing.NullPointerTester;
import io.spine.message.delivery.client.given.ExecutionCountingStrategy;
import io.spine.message.delivery.client.given.RunCountingOperationWithResult;
import io.spine.message.delivery.client.given.RunCountingVoidOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.message.delivery.client.given.RunCountingOperationWithResult.newRunCountingOperationWithResult;
import static io.spine.message.delivery.client.given.RunCountingVoidOperation.newRunCountingVoidOperation;

@DisplayName("StrategyChain should")
final class StrategyChainTest {

    private RequestExecutionStrategy strategy;

    private ExecutionCountingStrategy firstStrategy;
    private ExecutionCountingStrategy secondStrategy;

    @BeforeEach
    void setup() {
        firstStrategy = new ExecutionCountingStrategy();
        secondStrategy = new ExecutionCountingStrategy();
        strategy = StrategyChain.with(firstStrategy)
                                .then(secondStrategy)
                                .build();
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
    @DisplayName("use only first strategy if `VoidOperation` succeeds")
    void useOnlyFirstStrategyWithVoidOperation() {
        RunCountingVoidOperation operation = newRunCountingVoidOperation();
        strategy.runWith(operation);

        assertThat(operation.runCount()).isEqualTo(1);
        assertThat(firstStrategy.voidOperationExecutions()).isEqualTo(1);
        assertThat(firstStrategy.operationsWithResultExecutions()).isEqualTo(0);

        assertThat(secondStrategy.voidOperationExecutions()).isEqualTo(0);
        assertThat(secondStrategy.operationsWithResultExecutions()).isEqualTo(0);
    }

    @Test
    @DisplayName("use only first strategy `OperationWithResult` succeeds")
    void useOnlyFirstStrategyWithOperationWithResult() {
        RunCountingOperationWithResult operation = newRunCountingOperationWithResult();
        strategy.runWith(operation);

        assertThat(operation.runCount()).isEqualTo(1);
        assertThat(firstStrategy.voidOperationExecutions()).isEqualTo(0);
        assertThat(firstStrategy.operationsWithResultExecutions()).isEqualTo(1);

        assertThat(secondStrategy.voidOperationExecutions()).isEqualTo(0);
        assertThat(secondStrategy.operationsWithResultExecutions()).isEqualTo(0);
    }

    @Test
    @DisplayName("use other strategy `VoidOperation` failed")
    void useOtherStrategiesOnVoidOperationFailed() {
        strategy.runWith(RunCountingVoidOperation.throwUntil(2));

        assertThat(firstStrategy.voidOperationExecutions()).isEqualTo(1);
        assertThat(firstStrategy.operationsWithResultExecutions()).isEqualTo(0);

        assertThat(secondStrategy.voidOperationExecutions()).isEqualTo(1);
        assertThat(secondStrategy.operationsWithResultExecutions()).isEqualTo(0);
    }

    @Test
    @DisplayName("use other strategy `OperationWithResult` failed")
    void useOtherStrategiesOnOperationWithResultFailed() {
        strategy.runWith(RunCountingOperationWithResult.throwUntil(2));

        assertThat(firstStrategy.voidOperationExecutions()).isEqualTo(0);
        assertThat(firstStrategy.operationsWithResultExecutions()).isEqualTo(1);

        assertThat(secondStrategy.voidOperationExecutions()).isEqualTo(0);
        assertThat(secondStrategy.operationsWithResultExecutions()).isEqualTo(1);
    }
}
