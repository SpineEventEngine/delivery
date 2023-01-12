/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client.given;

import io.spine.message.delivery.client.RequestWithResult;

import static java.lang.String.format;

/**
 * Request for testing purposes that count executions of itself.
 *
 * <p>Can be instructed to throw exceptions.
 */
public final class RunCountingRequestWithResult implements RequestWithResult<String> {

    private int runCount = 0;

    private final boolean shouldThrow;
    private final int throwUntil;

    /**
     * Creates a new {@code RunCountingRequestWithResult} that doesn't throw any exceptions.
     */
    public static RunCountingRequestWithResult newRunCountingRequestWithResult() {
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
     * Returns the amount of tomes the {@linkplain #evaluate()} method was called.
     */
    public int runCount() {
        return runCount;
    }
}
