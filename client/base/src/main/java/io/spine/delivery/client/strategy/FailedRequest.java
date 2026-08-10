/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.client.strategy;

import com.google.common.collect.ImmutableList;

import java.util.function.Supplier;

/**
 * Summary of the {@code RequestWithResult} failure.
 *
 * @param <R>
 *         type of the result that has to be returned by the {@code RequestWithResult}.
 */
public final class FailedRequest<R> extends AbstractFailedRequest<ActionWithResult<R>> {

    private final Supplier<R> retry;

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
        super(allExceptions);
        this.retry = retry;
    }

    /**
     * Returns a predefined {@code ActionWithResult} that retries
     * failed {@code RequestWithResult}.
     */
    @Override
    public ActionWithResult<R> retry() {
        return retry::get;
    }
}
