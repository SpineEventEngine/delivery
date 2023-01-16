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
final class FailureReport {

    private final Runnable retry;

    private final RuntimeException lastException;

    private final ImmutableList<RuntimeException> previousExceptions;

    FailureReport(Runnable retry,
                  ImmutableList<RuntimeException> previousExceptions,
                  RuntimeException lastException) {
        this.retry = retry;
        this.previousExceptions = previousExceptions;
        this.lastException = lastException;
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
