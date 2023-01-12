/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import com.google.common.collect.ImmutableList;

/**
 * Occurs when a {@link RequestExecutionStrategy} failed to recover after exception
 * occurred during the request execution.
 */
public final class ExecutionFailedException extends RuntimeException {

    private static final long serialVersionUID = -5015255727872841600L;
    private final ImmutableList<Exception> causes;

    public ExecutionFailedException(ImmutableList<Exception> causes) {
        super("Error sending the request to the Liquor server, errors during the request execution could not be handled.");
        this.causes = causes;
    }

    /**
     * Returns exceptions occurred during request execution.
     *
     * <p>Each element of the list is an exception occurred during a single try of
     * the request execution. Depending on the implementation of
     * the {@link RequestExecutionStrategy} there may be several retries.
     *
     * <p>The exception thrown first will be the first element in the list.
     */
    public ImmutableList<Exception> causes() {
        return causes;
    }
}
