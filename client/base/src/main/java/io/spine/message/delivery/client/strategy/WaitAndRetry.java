/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client.strategy;

import com.google.common.collect.ImmutableList;
import io.spine.message.delivery.client.ExecutionFailedException;

import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * An error handling strategy that will be waiting a certain amount of time before
 * retrying a failed request.
 */
public final class WaitAndRetry extends AbstractExecutionStrategy {

    private final int waitSeconds;
    private final int retryCount;

    private int attempts = 0;

    /**
     * Starts creation chain for the {@code WaitAndRetry} strategy.
     *
     * @param seconds
     *         amount of seconds to wait before trying to execute failed request again.
     */
    public static Builder forSeconds(int seconds) {
        checkArgument(seconds > 0, "Number of seconds must be positive. Encountered: %s.", seconds);
        return new Builder(seconds);
    }

    private WaitAndRetry(int waitSeconds, int retryCount) {
        super();
        this.waitSeconds = waitSeconds;
        this.retryCount = retryCount;
    }

    @Override
    protected <R> Supplier<R> handleException(Exception e, Supplier<R> operation) {
        attempts++;
        if (attempts >= retryCount) {
            throw new ExecutionFailedException(ImmutableList.of(e));
        }
        sleepUninterruptibly(waitSeconds, SECONDS);
        return operation;
    }

    @Override
    protected Runnable handleException(Exception e, Runnable operation) {
        attempts++;
        if (attempts >= retryCount) {
            throw new ExecutionFailedException(ImmutableList.of(e));
        }
        sleepUninterruptibly(waitSeconds, SECONDS);
        return operation;
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
