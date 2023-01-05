/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import com.google.common.collect.ImmutableList;

/**
 * Occurs when a strategy failed to recover after exception thrown by an operation.
 */
public class StrategyFailedException extends RuntimeException {

    private static final long serialVersionUID = -5015255727872841600L;
    private final ImmutableList<Exception> occurredExceptions;

    public StrategyFailedException(ImmutableList<Exception> exceptions) {
        super("Error strategy was unable to handle errors.");
        occurredExceptions = exceptions;
    }

    /**
     * Returns exceptions occurred during operation execution.
     */
    public ImmutableList<Exception> occurredExceptions() {
        return occurredExceptions;
    }
}
