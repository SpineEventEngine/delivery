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
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * An error handling strategy that will be waiting a certain amount of time before
 * retrying a failed operation.
 */
public final class WaitAndRetry implements RequestExecutionStrategy {

    private final int waitSeconds;
    private final int retryCount;

    /**
     * Starts creation chain for the {@code WaitAndRetry} strategy.
     *
     * @param seconds
     *         amount of seconds to wait before trying to execute failed operation again.
     */
    public static Builder forSeconds(int seconds) {
        checkArgument(seconds > 0, "Amount seconds should be positive. Encountered: %s.", seconds);
        return new Builder(seconds);
    }

    private WaitAndRetry(int waitSeconds, int retryCount) {
        this.waitSeconds = waitSeconds;
        this.retryCount = retryCount;
    }

    /**
     * Executes the given operation and in case of failure waits some time before another attempt.
     *
     * @throws ExecutionFailedException
     *         if the retry attempts exceeded.
     */
    @Override
    public void runWithStrategy(VoidOperation operation) throws ExecutionFailedException {
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
                if (attempts < retryCount) {
                    sleepUninterruptibly(waitSeconds, SECONDS);
                }
            }
        }
        throw new ExecutionFailedException(ImmutableList.copyOf(caughtExceptions));
    }

    /**
     * Executes the given operation and in case of failure waits some time before another attempt.
     *
     * @throws ExecutionFailedException
     *         if the retry attempts exceeded.
     */
    @Override
    public <R> R runWithStrategy(OperationWithResult<R> operation) throws ExecutionFailedException {
        checkNotNull(operation);
        int attempts = 0;
        List<RuntimeException> caughtExceptions = new ArrayList<>(retryCount);
        while (attempts < retryCount) {
            try {
                return operation.run();
            } catch (RuntimeException e) {
                caughtExceptions.add(e);
                attempts++;
                if (attempts < retryCount) {
                    sleepUninterruptibly(waitSeconds, SECONDS);
                }
            }
        }
        throw new ExecutionFailedException(ImmutableList.copyOf(caughtExceptions));
    }

    /**
     * Builder for the {@code WaitAndRetry} strategy.
     */
    public static class Builder {

        private final int waitSeconds;

        private Builder(int waitSeconds) {
            this.waitSeconds = waitSeconds;
        }

        /**
         * Creates a new {@code WaitAndRetry} strategy with the given amount of retries.
         */
        public WaitAndRetry times(int n) {
            checkArgument(n > 0, "A positive retry amount expected. Encountered: %s.", n);
            return new WaitAndRetry(waitSeconds, n);
        }
    }
}
