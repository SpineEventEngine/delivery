/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client.failures;

/**
 * Strategy that executes operations and decides how to handle errors if any occurred.
 */
public interface ErrorHandlingStrategy {

    /**
     * Executes the given {@code VoidOperation} and handles occurred exceptions in the way
     * implemented in a particular strategy.
     *
     * @throws StrategyFailedException
     *         if strategy is unable to handle the occurred exception.
     */
    void runWithStrategy(VoidOperation operation) throws StrategyFailedException;

    /**
     * Executes the given {@code OperationWithResult} and handled occurred exceptions in the way
     * implemented in a particular strategy.
     *
     * @throws StrategyFailedException
     *         if strategy is unable to handle the occurred exception.
     */
    <R> R runWithStrategy(OperationWithResult<R> operation) throws StrategyFailedException;
}
