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
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * An error handling strategy that will be waiting a certain amount of time before retrying a failed
 * operation.
 */
public final class WaitAndRetry implements ErrorHandlingStrategy {

    private final int waitSeconds;
    private final int retryCount;

    /**
     * Starts creation chain for the {@code WaitAndRetry} strategy.
     *
     * @param seconds
     *         amount of seconds to wait before trying to execute failed operation again.
     */
    public static Builder waitForSeconds(int seconds) {
        checkArgument(seconds > 0, "Waiting seconds should be positive.");
        return new Builder(seconds);
    }

    private WaitAndRetry(int waitSeconds, int retryCount) {
        this.waitSeconds = waitSeconds;
        this.retryCount = retryCount;
    }

    /**
     * Executes the given operation and in case of failure waits some time before another attempt.
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
                if (attempts < retryCount) {
                    sleepUninterruptibly(waitSeconds, SECONDS);
                }
            }
        }
        if (!success) {
            throw new StrategyFailedException(ImmutableList.copyOf(caughtExceptions));
        }
    }

    /**
     * Executes the given operation and in case of failure waits some time before another attempt.
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
                if (attempts < retryCount) {
                    sleepUninterruptibly(waitSeconds, SECONDS);
                }
            }
        }
        throw new StrategyFailedException(ImmutableList.copyOf(caughtExceptions));
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
            checkArgument(n > 0, "Retry counts should be positive.");
            return new WaitAndRetry(waitSeconds, n);
        }
    }
}
