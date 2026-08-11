/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.client.strategy;

import com.google.common.collect.ImmutableList;

/**
 * Summary of the {@code VoidRequest} failure.
 */
public final class FailedVoidRequest extends AbstractFailedRequest<Action> {

    private final Runnable retry;

    /**
     * Creates a new {@code FailedVoidRequest} with the given {@code retry} function and
     * previously occurred exceptions.
     *
     * @param retry
     *         function that will be retrying the original request.
     * @param allExceptions
     *         previously occurred exceptions.
     */
    FailedVoidRequest(Runnable retry, ImmutableList<RuntimeException> allExceptions) {
        super(allExceptions);
        this.retry = retry;
    }

    /**
     * Returns a predefined {@code Action} that retries failed {@code VoidRequest}.
     */
    @Override
    public Action retry() {
        return retry::run;
    }
}
