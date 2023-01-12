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

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Basic implementation of the {@code RequestExecutionStrategy} that executes requests and delegates
 * error handling, if any, to subclasses.
 */
public abstract class AbstractExecutionStrategy implements RequestExecutionStrategy {

    @Override
    public final void execute(VoidRequest request) throws ExecutionFailedException {
        checkNotNull(request);
        while (true) {
            try {
                request.run();
                return;
            } catch (RuntimeException e) {
                handleException(e);
            }
        }
    }

    @Override
    public final <R> R evaluate(RequestWithResult<R> request) throws ExecutionFailedException {
        checkNotNull(request);
        while (true) {
            try {
                return request.evaluate();
            } catch (RuntimeException e) {
                handleException(e);
            }
        }
    }

    /**
     * Handles the given exception as meant by the strategy.
     *
     * <p>This method will be called only if some exception occurred during the request execution.
     *
     * <p>If this method returns without throwing an exception, it means that some recovery steps
     * were taken and the strategy can retry the request execution. The strategy will be retrying
     * request executions until this method throws {@code ExecutionFailedException}.
     */
    protected abstract void handleException(RuntimeException e) throws ExecutionFailedException;
}
