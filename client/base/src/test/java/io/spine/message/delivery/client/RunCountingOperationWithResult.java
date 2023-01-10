/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import static java.lang.String.format;

/**
 * Operation for testing purposes that count executions of itself.
 *
 * <p> Can be instructed to throw exceptions.
 */
final class RunCountingOperationWithResult implements OperationWithResult<String> {

    private int runCount = 0;

    private final boolean shouldThrow;
    private final int throwUntil;

    /**
     * Creates a new {@code RunCountingOperationWithResult} that doesn't throw any exceptions.
     */
    static RunCountingOperationWithResult newRunCountingOperationWithResult() {
        return new RunCountingOperationWithResult();
    }

    /**
     * Creates a new {@code RunCountingOperationWithResult} that throws {@code RuntimeException}s
     * until the {@linkplain #run()} method will be executed {@code throwTimes} times.
     */
    static RunCountingOperationWithResult throwUntil(int throwTimes) {
        return new RunCountingOperationWithResult(throwTimes);
    }

    private RunCountingOperationWithResult(int throwTimes) {
        this.shouldThrow = true;
        this.throwUntil = throwTimes;
    }

    private RunCountingOperationWithResult() {
        this.shouldThrow = false;
        this.throwUntil = 0;
    }

    /**
     * Throws a {@code RuntimeException} if instructed to do so or returns {@code Test} as a string.
     */
    @Override
    public String run() {
        runCount++;
        if (shouldThrow && runCount < throwUntil) {
            throw new RuntimeException(format("Instructed to throw on %d execution.", runCount));
        }
        return "Test";
    }

    /**
     * Returns the amount of tomes the {@linkplain #run()} method was called.
     */
    public int runCount() {
        return runCount;
    }
}
