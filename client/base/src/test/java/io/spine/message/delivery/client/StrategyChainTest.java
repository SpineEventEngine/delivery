/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import com.google.common.testing.NullPointerTester;
import io.spine.message.delivery.client.given.ExecutionCountingStrategy;
import io.spine.message.delivery.client.given.RunCountingRequestWithResult;
import io.spine.message.delivery.client.given.RunCountingVoidRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.message.delivery.client.given.RunCountingRequestWithResult.newRunCountingRequestWithResult;
import static io.spine.message.delivery.client.given.RunCountingVoidRequest.newRunCountingVoidRequest;

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
    @DisplayName("use only first strategy if `VoidRequest` succeeds")
    void useOnlyFirstStrategyWithVoidRequest() {
        RunCountingVoidRequest operation = newRunCountingVoidRequest();
        strategy.execute(operation);

        assertThat(operation.runCount()).isEqualTo(1);
        assertThat(firstStrategy.voidExecutions()).isEqualTo(1);
        assertThat(firstStrategy.withResultEvaluations()).isEqualTo(0);

        assertThat(secondStrategy.voidExecutions()).isEqualTo(0);
        assertThat(secondStrategy.withResultEvaluations()).isEqualTo(0);
    }

    @Test
    @DisplayName("use only first strategy `RequestWithResult` succeeds")
    void useOnlyFirstStrategyWithRequestWithResult() {
        RunCountingRequestWithResult operation = newRunCountingRequestWithResult();
        strategy.evaluate(operation);

        assertThat(operation.runCount()).isEqualTo(1);
        assertThat(firstStrategy.voidExecutions()).isEqualTo(0);
        assertThat(firstStrategy.withResultEvaluations()).isEqualTo(1);

        assertThat(secondStrategy.voidExecutions()).isEqualTo(0);
        assertThat(secondStrategy.withResultEvaluations()).isEqualTo(0);
    }

    @Test
    @DisplayName("use other strategy `VoidRequest` failed")
    void useOtherStrategiesOnVoidRequestFailed() {
        strategy.execute(RunCountingVoidRequest.throwUntil(2));

        assertThat(firstStrategy.voidExecutions()).isEqualTo(1);
        assertThat(firstStrategy.withResultEvaluations()).isEqualTo(0);

        assertThat(secondStrategy.voidExecutions()).isEqualTo(1);
        assertThat(secondStrategy.withResultEvaluations()).isEqualTo(0);
    }

    @Test
    @DisplayName("use other strategy `RequestWithResult` failed")
    void useOtherStrategiesOnRequestWithResultFailed() {
        strategy.evaluate(RunCountingRequestWithResult.throwUntil(2));

        assertThat(firstStrategy.voidExecutions()).isEqualTo(0);
        assertThat(firstStrategy.withResultEvaluations()).isEqualTo(1);

        assertThat(secondStrategy.voidExecutions()).isEqualTo(0);
        assertThat(secondStrategy.withResultEvaluations()).isEqualTo(1);
    }
}
