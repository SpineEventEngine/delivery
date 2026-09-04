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

package io.spine.delivery.client;

import com.google.common.testing.NullPointerTester;
import io.spine.delivery.client.given.RunCountingRequestWithResult;
import io.spine.delivery.client.given.RunCountingVoidRequest;
import io.spine.delivery.client.strategy.RetryImmediately;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.delivery.client.given.RunCountingRequestWithResult.neverThrowing;
import static io.spine.delivery.client.given.RunCountingVoidRequest.newRunCountingVoidRequest;
import static io.spine.delivery.client.given.RunCountingVoidRequest.throwUntil;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DisplayName("`RetryImmediately` error handling strategy should")
final class RetryImmediatelyTest {

    private RequestExecutionStrategy strategy;

    @BeforeEach
    void setup() {
        strategy = RetryImmediately.times(3);
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
    @DisplayName("execute `VoidRequest`")
    void executeVoidRequest() {
        var operation = newRunCountingVoidRequest();
        strategy.execute(operation);

        assertThat(operation.runCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("execute `RequestWithResult`")
    void executeRequestWithResult() {
        var operation = neverThrowing();
        strategy.evaluate(operation);

        assertThat(operation.runCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("retry `VoidRequest` on failure.")
    void retryVoidRequestOnFailure() {
        var operation = throwUntil(2);
        strategy.execute(operation);

        assertThat(operation.runCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("retry `RequestWithResult` on failure.")
    void retryRequestWithResultOnFailure() {
        var operation = RunCountingRequestWithResult.throwUntil(2);
        strategy.evaluate(operation);

        assertThat(operation.runCount()).isEqualTo(2);
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
