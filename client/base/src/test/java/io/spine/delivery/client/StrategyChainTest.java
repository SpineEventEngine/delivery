/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.client;

import com.google.common.testing.NullPointerTester;
import io.spine.delivery.client.given.ExecutionCountingStrategy;
import io.spine.delivery.client.given.RunCountingRequestWithResult;
import io.spine.delivery.client.given.RunCountingVoidRequest;
import io.spine.delivery.client.strategy.StrategyChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.delivery.client.given.RunCountingRequestWithResult.newRunCountingRequestWithResult;
import static io.spine.delivery.client.given.RunCountingVoidRequest.newRunCountingVoidRequest;

@DisplayName("`StrategyChain` should")
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
        var tester = new NullPointerTester();
        tester.testAllPublicInstanceMethods(strategy);
        tester.testAllPublicConstructors(strategy.getClass());
        tester.testAllPublicStaticMethods(strategy.getClass());
    }

    @Test
    @DisplayName("use only first strategy if `VoidRequest` succeeds")
    void useOnlyFirstStrategyWithVoidRequest() {
        var operation = newRunCountingVoidRequest();
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
        var operation = newRunCountingRequestWithResult();
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
