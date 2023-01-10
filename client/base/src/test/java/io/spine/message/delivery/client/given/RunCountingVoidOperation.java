/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client.given;

import io.spine.message.delivery.client.VoidOperation;

import static java.lang.String.format;

/**
 * Operation for testing purposes that count executions of itself.
 *
 * <p> Can be instructed to throw exceptions.
 */
public final class RunCountingVoidOperation implements VoidOperation {

    private int runCount = 0;
    private final boolean shouldThrow;
    private final int throwUntil;

    /**
     * Creates a new {@code RunCountingVoidOperation} that doesn't throw any exceptions.
     */
    public static RunCountingVoidOperation newRunCountingVoidOperation() {
        return new RunCountingVoidOperation();
    }

    /**
     * Creates a new {@code RunCountingVoidOperation} that throws {@code RuntimeException}s
     * until the {@linkplain #run()} method will be executed {@code throwTimes} times.
     */
    public static RunCountingVoidOperation throwUntil(int throwTimes) {
        return new RunCountingVoidOperation(throwTimes);
    }

    private RunCountingVoidOperation(int throwUntil) {
        this.shouldThrow = true;
        this.throwUntil = throwUntil;
    }

    private RunCountingVoidOperation() {
        this.shouldThrow = false;
        this.throwUntil = 0;
    }

    /**
     * Throws a {@code RuntimeException} if instructed to do so or does nothing.
     */
    @Override
    public void run() {
        runCount++;
        if (shouldThrow && runCount < throwUntil) {
            throw new RuntimeException(format("Instructed to throw on %d execution.", runCount));
        }
    }

    /**
     * Returns the amount of tomes the {@linkplain #run()} method was called.
     */
    public int runCount() {
        return runCount;
    }

}
