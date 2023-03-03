/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import com.google.common.collect.ImmutableList;

import static java.lang.System.lineSeparator;

/**
 * Occurs when a {@link RequestExecutionStrategy} failed to recover after exception
 * occurred during the request execution.
 */
public final class ExecutionFailedException extends RuntimeException {

    private static final long serialVersionUID = -5015255727872841600L;
    private final ImmutableList<RuntimeException> causes;

    /**
     * Creates a new {@code ExecutionFailedException} with the given {@code causes}.
     */
    public ExecutionFailedException(ImmutableList<RuntimeException> causes) {
        super(formatMessageFor(causes));
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
    public ImmutableList<RuntimeException> causes() {
        return causes;
    }

    /**
     * Creates an error message including messages from all {@code causes}.
     */
    private static String formatMessageFor(Iterable<RuntimeException> causes) {
        StringBuilder builder = new StringBuilder(
                "Error sending the request to the Liquor server, errors during the request execution could not be handled."
        );
        causes.forEach(cause -> builder.append(lineSeparator())
                                       .append("- Caused by: ")
                                       .append(cause.getMessage()));
        return builder.toString();
    }
}
