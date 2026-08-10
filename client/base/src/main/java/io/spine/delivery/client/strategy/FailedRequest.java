/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.client.strategy;

import com.google.common.collect.ImmutableList;
import io.spine.delivery.client.ExecutionFailedException;

import java.util.function.Supplier;

/**
 * Summary of the {@code RequestWithResult} failure.
 *
 * @param <R>
 *         type of the result that has to be returned by the {@code RequestWithResult}.
 */
public final class FailedRequest<R> {

    private final Supplier<R> retry;

    private final ImmutableList<RuntimeException> allExceptions;

    /**
     * Creates a new {@code FailedRequest} with the given {@code retry} function and
     * previously occurred exceptions.
     *
     * @param retry
     *         function that will be retrying the original request.
     * @param allExceptions
     *         previously occurred exceptions.
     */
    FailedRequest(Supplier<R> retry, ImmutableList<RuntimeException> allExceptions) {
        FailedRequests.checkHasExceptions(allExceptions);
        this.retry = retry;
        this.allExceptions = allExceptions;
    }

    /**
     * Returns a predefined {@code ActionWithResult} that retries
     * failed {@code RequestWithResult}.
     */
    public ActionWithResult<R> retry() {
        return retry::get;
    }

    /**
     * Returns a predefined {@code ActionWithResult} that propagates current and previously
     * occurred exceptions as an {@code ExecutionFailedException}.
     */
    public ActionWithResult<R> propagate() {
        throw new ExecutionFailedException(allExceptions);
    }

    /**
     * Returns the last occurred exception.
     */
    public Exception lastException() {
        return allExceptions.get(allExceptions().size() - 1);
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
