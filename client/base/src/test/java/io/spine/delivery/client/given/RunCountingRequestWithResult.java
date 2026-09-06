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

package io.spine.delivery.client.given;

import io.spine.delivery.client.RequestWithResult;

import static java.lang.String.format;

/**
 * A request for testing purposes that counts executions of itself and returns a predefined
 * string result.
 *
 * <p>Can be instructed to throw exceptions.
 */
public final class RunCountingRequestWithResult implements RequestWithResult<String> {

    private int runCount = 0;

    private final boolean shouldThrow;
    private final int throwUntil;

    /**
     * Creates a new instance that never throws, complementing {@link #throwUntil(int)}.
     */
    public static RunCountingRequestWithResult neverThrowing() {
        return new RunCountingRequestWithResult();
    }

    /**
     * Creates a new {@code RunCountingRequestWithResult} that throws {@code RuntimeException}s
     * until the {@linkplain #evaluate()} method will be executed {@code throwTimes} times.
     */
    public static RunCountingRequestWithResult throwUntil(int throwTimes) {
        return new RunCountingRequestWithResult(throwTimes);
    }

    private RunCountingRequestWithResult(int throwTimes) {
        this.shouldThrow = true;
        this.throwUntil = throwTimes;
    }

    private RunCountingRequestWithResult() {
        this.shouldThrow = false;
        this.throwUntil = 0;
    }

    /**
     * Throws a {@code RuntimeException} if instructed to do so or returns {@code Test} as a string.
     */
    @Override
    public String evaluate() {
        runCount++;
        if (shouldThrow && runCount < throwUntil) {
            throw new RuntimeException(format("Instructed to throw on %d execution.", runCount));
        }
        return "Test";
    }

    /**
     * Returns the amount of times the {@linkplain #evaluate()} method was called.
     */
    public int runCount() {
        return runCount;
    }
}
