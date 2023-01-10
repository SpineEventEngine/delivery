/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import com.google.common.collect.ImmutableList;

/**
 * Occurs when a {@link RequestExecutionStrategy} failed to recover after exception
 * thrown by the request.
 */
public final class ExecutionFailedException extends RuntimeException {

    private static final long serialVersionUID = -5015255727872841600L;
    private final ImmutableList<Exception> causes;

    public ExecutionFailedException(ImmutableList<Exception> causes) {
        super("Strategy was unable to handle errors.");
        this.causes = causes;
    }

    /**
     * Returns exceptions occurred during operation execution.
     */
    public ImmutableList<Exception> causes() {
        return causes;
    }
}
