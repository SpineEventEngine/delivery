/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.client.strategy;

import com.google.common.collect.ImmutableList;
import io.spine.delivery.client.ExecutionFailedException;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A summary of a failed request.
 *
 * <p>Carries the exceptions that the request has caused so far, and offers the actions
 * an execution strategy may take in response to the failure.
 *
 * @param <A>
 *         the type of the action returned by {@link #retry()} and {@link #propagate()}
 */
public abstract class AbstractFailedRequest<A> {

    private final ImmutableList<RuntimeException> allExceptions;

    /**
     * Creates a new instance with the previously occurred exceptions.
     *
     * @param allExceptions
     *         previously occurred exceptions; must not be empty
     */
    protected AbstractFailedRequest(ImmutableList<RuntimeException> allExceptions) {
        checkHasExceptions(allExceptions);
        this.allExceptions = allExceptions;
    }

    /**
     * Ensures that the given list of exceptions is not empty.
     *
     * <p>A failure is always caused by at least one exception, so an empty list
     * means the failure has been reported by mistake.
     */
    private static void checkHasExceptions(ImmutableList<RuntimeException> allExceptions) {
        checkArgument(!allExceptions.isEmpty(), "The exception list should not be empty.");
    }

    /**
     * Returns a predefined action that retries the failed request.
     */
    public abstract A retry();

    /**
     * Returns a predefined action that propagates current and previously occurred
     * exceptions as an {@code ExecutionFailedException}.
     */
    public A propagate() {
        throw new ExecutionFailedException(allExceptions);
    }

    /**
     * Returns the last occurred exception.
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
