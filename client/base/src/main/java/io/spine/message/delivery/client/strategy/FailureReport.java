/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client.strategy;

import com.google.common.collect.ImmutableList;
import io.spine.message.delivery.client.ExecutionFailedException;

/**
 * Summary of the {@code VoidRequest} failure.
 */
public final class FailureReport {

    private final Runnable retry;

    private final ImmutableList<RuntimeException> allExceptions;

    FailureReport(Runnable retry, ImmutableList<RuntimeException> allExceptions) {
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
     */
    public ImmutableList<RuntimeException> allExceptions() {
        return allExceptions;
    }
}
