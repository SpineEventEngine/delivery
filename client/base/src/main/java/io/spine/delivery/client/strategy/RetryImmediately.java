/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.client.strategy;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Strategy that will be immediately retrying requests in case of failures.
 */
public final class RetryImmediately extends AbstractExecutionStrategy {

    private final int retryCount;

    private int attempts = 0;

    /**
     * Create a new {@code RetryImmediately} strategy with the given amount of retry attempts.
     */
    public static RetryImmediately times(int n) {
        checkArgument(n > 0, "A positive value expected. Encountered: %s.", n);
        return new RetryImmediately(n);
    }

    private RetryImmediately(int count) {
        super();
        retryCount = count;
    }

    @Override
    protected <R> ActionWithResult<R> handleException(FailedRequest<R> failure) {
        attempts++;
        if (attempts >= retryCount) {
            return failure.propagate();
        }
        return failure.retry();
    }

    @Override
    protected Action handleException(FailedVoidRequest failure) {
        attempts++;
        if (attempts >= retryCount) {
            return failure.propagate();
        }
        return failure.retry();
    }
}
