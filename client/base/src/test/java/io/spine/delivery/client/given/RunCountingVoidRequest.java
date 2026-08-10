/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.client.given;

import io.spine.delivery.client.VoidRequest;

import static java.lang.String.format;

/**
 * A request for testing purposes that counts executions of itself and returns nothing.
 *
 * <p>Can be instructed to throw exceptions.
 */
public final class RunCountingVoidRequest implements VoidRequest {

    private int runCount = 0;
    private final boolean shouldThrow;
    private final int throwUntil;

    /**
     * Creates a new {@code RunCountingVoidRequest} that doesn't throw any exceptions.
     */
    public static RunCountingVoidRequest newRunCountingVoidRequest() {
        return new RunCountingVoidRequest();
    }

    /**
     * Creates a new {@code RunCountingVoidRequest} that throws {@code RuntimeException}s
     * until the {@linkplain #run()} method will be executed {@code throwTimes} times.
     */
    public static RunCountingVoidRequest throwUntil(int throwTimes) {
        return new RunCountingVoidRequest(throwTimes);
    }

    private RunCountingVoidRequest(int throwUntil) {
        this.shouldThrow = true;
        this.throwUntil = throwUntil;
    }

    private RunCountingVoidRequest() {
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
     * Returns the amount of times the {@linkplain #run()} method was called.
     */
    public int runCount() {
        return runCount;
    }

}
