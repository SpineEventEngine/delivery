/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.client.strategy;

import com.google.common.collect.ImmutableList;
import io.spine.delivery.client.ExecutionFailedException;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Summary of the {@code VoidRequest} failure.
 */
public final class FailedVoidRequest {

    private final Runnable retry;

    private final ImmutableList<RuntimeException> allExceptions;

    /**
     * Create a new {@code FailedVoidRequest} with the given {@code retry} function and
     * previously occurred exceptions.
     *
     * @param retry
     *         function that will be retrying the original request.
     * @param allExceptions
     *         previously occurred exceptions.
     */
    FailedVoidRequest(Runnable retry, ImmutableList<RuntimeException> allExceptions) {
        checkArgument(!allExceptions.isEmpty(), "The exception list should not be empty.");
        this.retry = retry;
        this.allExceptions = allExceptions;
    }

    /**
     * Returns a predefined {@code Action} that retries failed {@code VoidRequest}.
     */
    public Action retry() {
        return retry::run;
    }

    /**
     * Returns a predefined {@code Action} that propagates current and previously occurred
     * exceptions as an {@code ExecutionFailedException}.
     */
    public Action propagate() {
        throw new ExecutionFailedException(allExceptions);
    }

    /**
     * Returns last occurred exception.
     */
    public Exception lastException() {
        return allExceptions.get(allExceptions.size() - 1);
    }

    /**
     * Returns a list of all occurred exceptions.
     *
     * <p>The exception thrown first will be the first element in the list. The last element in
     * the list is the exception that caused this failure.
     */
    public ImmutableList<RuntimeException> allExceptions() {
        return allExceptions;
    }
}
