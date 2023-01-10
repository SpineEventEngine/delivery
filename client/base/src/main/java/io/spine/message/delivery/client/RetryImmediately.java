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
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Strategy that will be immediately retrying operations in case of failures.
 */
public final class RetryImmediately implements RequestExecutionStrategy {

    private final int retryCount;

    /**
     * Create a new {@code RetryImmediately} strategy with the given amount of retry attempts.
     */
    public static RetryImmediately times(int n) {
        checkArgument(n > 0, "A positive value expected. Encountered: %s.", n);
        return new RetryImmediately(n);
    }

    private RetryImmediately(int count) {
        retryCount = count;
    }

    /**
     * Executes the given operation and in case of failure immediately performs another attempt.
     *
     * @throws ExecutionFailedException
     *         if the retry attempts exceeded.
     */
    @Override
    public void runWith(VoidOperation operation) throws ExecutionFailedException {
        checkNotNull(operation);
        int attempts = 0;
        List<RuntimeException> caughtExceptions = new ArrayList<>(retryCount);
        while (attempts < retryCount) {
            try {
                operation.run();
                return;
            } catch (RuntimeException e) {
                caughtExceptions.add(e);
                attempts++;
            }
        }
        throw new ExecutionFailedException(ImmutableList.copyOf(caughtExceptions));
    }

    /**
     * Executes the given operation and in case of failure immediately performs another attempt.
     *
     * @throws ExecutionFailedException
     *         if the retry attempts exceeded.
     */
    @Override
    public <R> R runWith(OperationWithResult<R> operation) throws ExecutionFailedException {
        checkNotNull(operation);
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
        throw new ExecutionFailedException(ImmutableList.copyOf(caughtExceptions));
    }
}
