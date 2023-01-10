/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import com.google.common.testing.NullPointerTester;
import io.spine.message.delivery.client.given.RunCountingRequestWithResult;
import io.spine.message.delivery.client.given.RunCountingVoidRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.message.delivery.client.given.RunCountingRequestWithResult.newRunCountingRequestWithResult;
import static io.spine.message.delivery.client.given.RunCountingVoidRequest.newRunCountingVoidRequest;
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
    @DisplayName("execute `VoidRequest`")
    void executeVoidRequest() {
        RunCountingVoidRequest operation = newRunCountingVoidRequest();
        strategy.execute(operation);

        assertThat(operation.runCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("execute `RequestWithResult`")
    void executeRequestWithResult() {
        RunCountingRequestWithResult operation = newRunCountingRequestWithResult();
        strategy.evaluate(operation);

        assertThat(operation.runCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("throw `StrategyFailedException` if `VoidRequest` failed.")
    void throwOnVoidRequestFailure() {
        assertThrows(ExecutionFailedException.class,
                     () -> strategy.execute(() -> {
                         throw new RuntimeException("For testing.");
                     })
        );
    }

    @Test
    @DisplayName("throw `StrategyFailedException` if `RequestWithResult` failed.")
    void throwOnRequestWithResultFailure() {
        assertThrows(ExecutionFailedException.class, () -> strategy.evaluate(() -> {
            throw new RuntimeException("For testing");
        }));
    }

    @Test
    @DisplayName("do not throw exceptions if `VoidRequest` succeeded.")
    void doNotThrowOnVoidRequestSuccess() {
        assertDoesNotThrow(() -> strategy.execute(() -> {
        }));
    }

    @Test
    @DisplayName("do not throw exceptions if `RequestWithResult` succeeded.")
    void doNotThrowOnRequestWithResultSuccess() {
        assertDoesNotThrow(() -> strategy.evaluate(() -> "Test"));
    }
}
