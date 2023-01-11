/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client.strategy;

import com.google.common.collect.ImmutableList;
import io.spine.message.delivery.client.ExecutionFailedException;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Strategy that will be immediately retrying requests in case of failures.
 */
public final class RetryImmediately extends AbstractExecutionStrategy {

    private final int retryCount;

    private int attempts = 0;

    private final List<RuntimeException> occurredExceptions = new ArrayList<>();

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
    protected void handleException(RuntimeException e) throws ExecutionFailedException {
        attempts++;
        occurredExceptions.add(e);
        if (attempts >= retryCount) {
            throw new ExecutionFailedException(ImmutableList.copyOf(occurredExceptions));
        }
    }
}
