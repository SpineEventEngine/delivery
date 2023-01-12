/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client.strategy;

import com.google.common.collect.ImmutableList;
import io.spine.message.delivery.client.ExecutionFailedException;
import io.spine.message.delivery.client.RequestExecutionStrategy;
import io.spine.message.delivery.client.RequestWithResult;
import io.spine.message.delivery.client.VoidRequest;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Basic implementation of the {@code RequestExecutionStrategy} that executes requests and delegates
 * error handling, if any, to subclasses.
 */
public abstract class AbstractExecutionStrategy implements RequestExecutionStrategy {

    @Override
    public final void execute(VoidRequest request) throws ExecutionFailedException {
        checkNotNull(request);
        List<RuntimeException> occurredExceptions = new ArrayList<>();
        while (true) {
            try {
                request.run();
                return;
            } catch (RuntimeException e) {
                occurredExceptions.add(e);
                if (handleException(e) == Decision.STOP_AND_THROW) {
                    throw new ExecutionFailedException(ImmutableList.copyOf(occurredExceptions));
                }
            }
        }
    }

    @Override
    public final <R> R evaluate(RequestWithResult<R> request) throws ExecutionFailedException {
        checkNotNull(request);
        List<RuntimeException> occurredExceptions = new ArrayList<>();
        while (true) {
            try {
                return request.evaluate();
            } catch (RuntimeException e) {
                occurredExceptions.add(e);
                if (handleException(e) == Decision.STOP_AND_THROW) {
                    throw new ExecutionFailedException(ImmutableList.copyOf(occurredExceptions));
                }
            }
        }
    }

    /**
     * Handles the given exception as meant by the strategy.
     *
     * <p>This method will be called only if some exception occurred during the request execution.
     *
     * @return the {@code Decision} on what the strategy should do next
     */
    protected abstract Decision handleException(RuntimeException e);

    /**
     * A decision made by the error handler about the next steps to take by the execution strategy.
     */
    protected enum Decision {

        /**
         * Retry the request execution.
         */
        RETRY,

        /**
         * Stop the execution and throw exception.
         */
        STOP_AND_THROW,
    }
}
