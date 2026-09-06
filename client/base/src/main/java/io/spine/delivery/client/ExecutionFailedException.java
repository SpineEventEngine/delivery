/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.spine.delivery.client;

import com.google.common.collect.ImmutableList;

import static com.google.common.base.Throwables.getStackTraceAsString;
import static java.lang.System.lineSeparator;

/**
 * Occurs when a {@link RequestExecutionStrategy} failed to recover after an exception
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
     * Returns exceptions occurred during the request execution.
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
        var builder = new StringBuilder(
                "Error sending the request to the Delivery server, errors during "
                        + "the request execution could not be handled."
        );
        causes.forEach(cause -> builder.append(lineSeparator())
                                       .append("- Caused by: ")
                                       .append(cause.getMessage())
                                       .append(lineSeparator())
                                       .append("- Stacktrace: ")
                                       .append(getStackTraceAsString(cause))
                                       .append("- - - - - - - - - -")
        );
        return builder.toString();
    }
}
