/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client.strategy;

import io.spine.message.delivery.client.ExecutionFailedException;
import io.spine.message.delivery.client.RequestExecutionStrategy;
import io.spine.message.delivery.client.RequestWithResult;
import io.spine.message.delivery.client.VoidRequest;

import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Basic implementation of the {@code RequestExecutionStrategy} that executes requests and delegates
 * error handling, if any, to subclasses.
 */
public abstract class AbstractExecutionStrategy implements RequestExecutionStrategy {

    @Override
    public final void execute(VoidRequest request) throws ExecutionFailedException {
        checkNotNull(request);
        try {
            request.run();
        } catch (RuntimeException e) {
            handleException(e, () -> execute(request)).run();
        }
    }

    @Override
    public final <R> R evaluate(RequestWithResult<R> request) throws ExecutionFailedException {
        checkNotNull(request);
        try {
            return request.evaluate();
        } catch (RuntimeException e) {
            return this.handleException(e, () -> evaluate(request))
                       .get();
        }
    }

    /**
     * Handles the exception occurred during the {@code RequestWithResult} execution.
     *
     * @param e
     *         occurred exception
     * @param operation
     *         executable operation
     * @param <R>
     *         type of the result
     * @return an action that should be executed
     */
    protected abstract <R> Supplier<R> handleException(Exception e, Supplier<R> operation);

    /**
     * Handles an exception occurred during the {@code VoidRequest} execution.
     *
     * @param e
     *         occurred exception
     * @param operation
     *         executable operation
     * @return an action that should be executed
     */
    protected abstract Runnable handleException(Exception e, Runnable operation);
}
