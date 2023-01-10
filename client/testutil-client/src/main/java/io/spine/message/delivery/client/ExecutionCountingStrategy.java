/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import com.google.common.collect.ImmutableList;

/**
 * A strategy for testing that counts amount of operstions executed.
 */
public final class ExecutionCountingStrategy implements RequestExecutionStrategy {

    private int voidRequestExecutions = 0;

    private int requestWithResultExecutions = 0;

    /**
     * Increments the execution counter for {@code VoidRequest} and executes
     * the given {@code operation}.
     *
     * <p> Even if the operation throws an exception it will be counted as execution.
     *
     * @throws ExecutionFailedException if the operation throws an exception.
     */
    @Override
    public void execute(VoidRequest operation) throws ExecutionFailedException {
        voidRequestExecutions++;
        try {
            operation.run();
        } catch (RuntimeException e) {
            throw new ExecutionFailedException(ImmutableList.of(e));
        }
    }

    /**
     * Increments the execution counter for {@code RequestWithResult} and executes
     * the given {@code operation}.
     *
     * <p> Even if the operation throws an exception it will be counted as execution.
     *
     * @throws ExecutionFailedException if the operation throws an exception.
     */
    @Override
    public <R> R evaluate(RequestWithResult<R> operation) throws ExecutionFailedException {
        requestWithResultExecutions++;
        try {
            return operation.evaluate();
        } catch (RuntimeException e) {
            throw new ExecutionFailedException(ImmutableList.of(e));
        }
    }

    /**
     * Returns a number of {@code VoidRequest}s executed with this strategy.
     */
    public int voidExecutions() {
        return voidRequestExecutions;
    }

    /**
     * Returns a number of {@code RequestWithResult} executed with this strategy.
     */
    public int withResultEvaluations() {
        return requestWithResultExecutions;
    }
}
