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

    private int voidOperationExecutions = 0;

    private int operationsWithResultExecutions = 0;

    /**
     * Increments the execution counter for {@code VoidOperation} and executes
     * the given {@code operation}.
     *
     * <p> Even if the operation throws an exception it will be counted as execution.
     *
     * @throws ExecutionFailedException if the operation throws an exception.
     */
    @Override
    public void runWith(VoidOperation operation) throws ExecutionFailedException {
        voidOperationExecutions++;
        try {
            operation.run();
        } catch (RuntimeException e) {
            throw new ExecutionFailedException(ImmutableList.of(e));
        }
    }

    /**
     * Increments the execution counter for {@code OperationWithResult} and executes
     * the given {@code operation}.
     *
     * <p> Even if the operation throws an exception it will be counted as execution.
     *
     * @throws ExecutionFailedException if the operation throws an exception.
     */
    @Override
    public <R> R runWith(OperationWithResult<R> operation) throws ExecutionFailedException {
        operationsWithResultExecutions++;
        try {
            return operation.run();
        } catch (RuntimeException e) {
            throw new ExecutionFailedException(ImmutableList.of(e));
        }
    }

    /**
     * Returns a number of {@code VoidOperation}s executed with this strategy.
     */
    public int voidOperationExecutions() {
        return voidOperationExecutions;
    }

    /**
     * Returns a number of {@code OperationWithResult} executed with this strategy.
     */
    public int operationsWithResultExecutions() {
        return operationsWithResultExecutions;
    }
}
