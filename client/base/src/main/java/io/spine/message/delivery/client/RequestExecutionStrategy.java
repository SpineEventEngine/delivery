/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

/**
 * Strategy that executes operations and decides how to handle errors if any occurred.
 */
public interface RequestExecutionStrategy {

    /**
     * Executes the given {@code VoidOperation} and handles occurred exceptions in the way
     * implemented in a particular strategy.
     *
     * @throws ExecutionFailedException
     *         if strategy is unable to handle the occurred exception.
     */
    void runWith(VoidOperation operation) throws ExecutionFailedException;

    /**
     * Executes the given {@code OperationWithResult} and handled occurred exceptions in the way
     * implemented in a particular strategy.
     *
     * @throws ExecutionFailedException
     *         if strategy is unable to handle the occurred exception.
     */
    <R> R runWith(OperationWithResult<R> operation) throws ExecutionFailedException;
}
