/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client.strategy;

import com.google.common.collect.ImmutableList;
import io.spine.message.delivery.client.ExecutionFailedException;

import java.util.function.Supplier;

/**
 * Summary of the {@code RequestWithResult} failure.
 *
 * @param <R>
 *         type of result that have to be returned by the {@code RequestWithResult}.
 */
final class FailureReportForNonVoidRequest<R> {

    private final Supplier<R> retry;

    private final RuntimeException lastException;

    private final ImmutableList<RuntimeException> previousExceptions;

    FailureReportForNonVoidRequest(Supplier<R> retry,
                                   ImmutableList<RuntimeException> previousExceptions,
                                   RuntimeException lastException) {
        this.retry = retry;
        this.previousExceptions = previousExceptions;
        this.lastException = lastException;
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
        ImmutableList<RuntimeException> exceptions = ImmutableList.<RuntimeException>builder()
                .addAll(previousExceptions)
                .add(lastException)
                .build();
        throw new ExecutionFailedException(exceptions);
    }

    /**
     * Returns last occurred exception.
     */
    public Exception lastException() {
        return lastException;
    }

    /**
     * Returns a list of all previously occurred exceptions.
     */
    public ImmutableList<RuntimeException> previousExceptions() {
        return previousExceptions;
    }
}
