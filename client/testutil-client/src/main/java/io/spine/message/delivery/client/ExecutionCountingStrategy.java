/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import com.google.common.collect.ImmutableList;
import io.spine.message.delivery.client.failures.ErrorHandlingStrategy;
import io.spine.message.delivery.client.failures.OperationWithResult;
import io.spine.message.delivery.client.failures.StrategyFailedException;
import io.spine.message.delivery.client.failures.VoidOperation;

/**
 * A strategy for testing that counts amount of operstions executed.
 */
public final class ExecutionCountingStrategy implements ErrorHandlingStrategy {

    private int voidOperationExecutions = 0;

    private int operationsWithResultExecutions = 0;

    /**
     * Increments the {@code voidOperationExecutions} and executes the given {@code operation}.
     *
     * Even if the operation throws an exception it will be counted as execution.
     *
     * @throws StrategyFailedException if the operation throws an exception.
     */
    @Override
    public void runWithStrategy(VoidOperation operation) throws StrategyFailedException {
        voidOperationExecutions++;
        try {
            operation.run();
        } catch (RuntimeException e) {
            throw new StrategyFailedException(ImmutableList.of(e));
        }
    }

    /**
     * Increments the {@code operationsWithResultExecutions} and executes
     * the given {@code operation}.
     *
     * Even if the operation throws an exception it will be counted as execution.
     *
     * @throws StrategyFailedException if the operation throws an exception.
     */
    @Override
    public <R> R runWithStrategy(OperationWithResult<R> operation) throws StrategyFailedException {
        operationsWithResultExecutions++;
        try {
            return operation.run();
        } catch (RuntimeException e) {
            throw new StrategyFailedException(ImmutableList.of(e));
        }
    }

    /**
     * Returns an amount of {@code VoidOperations} executed with this strategy.
     */
    public int voidOperationExecutions() {
        return voidOperationExecutions;
    }

    /**
     * Returns an amount of {@code OperationWithResult} executed with this strategy.
     */
    public int operationsWithResultExecutions() {
        return operationsWithResultExecutions;
    }
}
