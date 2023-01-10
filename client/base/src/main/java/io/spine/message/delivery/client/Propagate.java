/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import com.google.common.collect.ImmutableList;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Strategy that doesn't handle errors but propagates them if the operation failed.
 */
public final class Propagate implements RequestExecutionStrategy {

    /**
     * Executes the given {@code operation} and throws {@code StrategyFailedException} if
     * any exception occurs.
     *
     * @throws ExecutionFailedException
     *         if any exceptions occurs.
     */
    @Override
    public void runWithStrategy(VoidOperation operation) throws ExecutionFailedException {
        checkNotNull(operation);
        try {
            operation.run();
        } catch (RuntimeException e) {
            throw new ExecutionFailedException(ImmutableList.of(e));
        }
    }

    /**
     * Executes the given {@code operation} and throws {@code StrategyFailedException} if
     * any exception occurs.
     *
     * @throws ExecutionFailedException
     *         if any exceptions occurs.
     */
    @Override
    public <R> R runWithStrategy(OperationWithResult<R> operation) throws ExecutionFailedException {
        checkNotNull(operation);
        try {
            return operation.run();
        } catch (RuntimeException e) {
            throw new ExecutionFailedException(ImmutableList.of(e));
        }
    }
}
