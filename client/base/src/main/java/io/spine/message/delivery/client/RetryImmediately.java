/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * An error handling strategy that will be immediately retrying operations in case of failures.
 */
public final class RetryImmediately implements ErrorHandlingStrategy {

    private final int retryCount;

    /**
     * Create a new {@code RetryImmediately} strategy with the given amount of retry attempts.
     */
    public static RetryImmediately times(int n) {
        checkArgument(n > 0, "Retries count should be positive.");
        return new RetryImmediately(n);
    }

    private RetryImmediately(int count) {
        retryCount = count;
    }

    /**
     * Executes the given operation and in case of failure immediately performs another attempt.
     *
     * @throws StrategyFailedException
     *         if the retry attempts exceeded.
     */
    @Override
    public void runWithStrategy(VoidOperation operation) throws StrategyFailedException {
        int attempts = 0;
        boolean success = false;
        List<RuntimeException> caughtExceptions = new ArrayList<>(retryCount);
        while (attempts < retryCount && !success) {
            try {
                operation.run();
                success = true;
            } catch (RuntimeException e) {
                caughtExceptions.add(e);
                attempts++;
            }
        }
        if (!success) {
            throw new StrategyFailedException(ImmutableList.copyOf(caughtExceptions));
        }
    }

    /**
     * Executes the given operation and in case of failure immediately performs another attempt.
     *
     * @throws StrategyFailedException
     *         if the retry attempts exceeded.
     */
    @Override
    public <R> R runWithStrategy(OperationWithResult<R> operation) throws StrategyFailedException {
        int attempts = 0;
        List<RuntimeException> caughtExceptions = new ArrayList<>(retryCount);
        while (attempts < retryCount) {
            try {
                return operation.run();
            } catch (RuntimeException e) {
                caughtExceptions.add(e);
                attempts++;
            }
        }
        throw new StrategyFailedException(ImmutableList.copyOf(caughtExceptions));
    }
}
