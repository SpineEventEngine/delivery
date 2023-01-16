/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import com.google.common.testing.NullPointerTester;
import io.spine.message.delivery.client.given.RunCountingRequestWithResult;
import io.spine.message.delivery.client.given.RunCountingVoidRequest;
import io.spine.message.delivery.client.strategy.WaitAndRetry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.message.delivery.client.given.RunCountingRequestWithResult.newRunCountingRequestWithResult;
import static io.spine.message.delivery.client.given.RunCountingVoidRequest.newRunCountingVoidRequest;
import static io.spine.message.delivery.client.given.RunCountingVoidRequest.throwUntil;
import static java.lang.System.*;
import static java.time.Duration.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("`WaitAndRetry` error handling strategy should")
final class WaitAndRetryTest {

    private RequestExecutionStrategy strategy;

    @BeforeEach
    void setup() {
        strategy = WaitAndRetry.forSeconds(2)
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
    @DisplayName("execute `VoidRequest` without waiting")
    void executeVoidRequest() {
        RunCountingVoidRequest operation = newRunCountingVoidRequest();

        assertTimeout(ofSeconds(1), () -> strategy.execute(operation));
        assertThat(operation.runCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("execute `RequestWithResult` without waiting")
    void executeRequestWithResult() {
        RunCountingRequestWithResult operation = newRunCountingRequestWithResult();

        assertTimeout(ofSeconds(1), () -> strategy.evaluate(operation));
        assertThat(operation.runCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("retry `VoidRequest` on failure with waiting")
    void retryVoidRequestOnFailure() {
        RunCountingVoidRequest operation = throwUntil(2);

        long before = currentTimeMillis();
        strategy.execute(operation);
        long after = currentTimeMillis();

        assertThat(after - before).isAtLeast(2000);
        assertThat(operation.runCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("retry `RequestWithResult` on failure with waiting")
    void retryRequestWithResultOnFailure() {
        RunCountingRequestWithResult operation = RunCountingRequestWithResult.throwUntil(2);

        long before = currentTimeMillis();
        strategy.evaluate(operation);
        long after = currentTimeMillis();

        assertThat(after - before).isAtLeast(2000);
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
