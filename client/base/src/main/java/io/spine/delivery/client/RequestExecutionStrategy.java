/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.client;

/**
 * A strategy that executes requests and decides how to handle errors if any occurred.
 */
public interface RequestExecutionStrategy {

    /**
     * Executes the given {@code VoidRequest} and handles occurred exceptions in the way
     * implemented in a particular strategy.
     *
     * @throws ExecutionFailedException
     *         if the strategy is unable to handle the occurred exception
     */
    void execute(VoidRequest operation) throws ExecutionFailedException;

    /**
     * Executes the given {@code RequestWithResult} and handles occurred exceptions in the way
     * implemented in a particular strategy.
     *
     * @throws ExecutionFailedException
     *         if the strategy is unable to handle the occurred exception
     */
    <R> R evaluate(RequestWithResult<R> operation) throws ExecutionFailedException;
}
