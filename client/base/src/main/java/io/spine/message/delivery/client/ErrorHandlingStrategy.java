/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

/**
 * Strategy that executes operations and decides how to handle errors if any occurred.
 */
public interface ErrorHandlingStrategy {

    /**
     * Executes the given {@code VoidOperation} and handles occurred exceptions.
     */
    void runWithStrategy(VoidOperation operation);

    /**
     * Executes the given {@code OperationWithResult} and handled occurred exceptions.
     */
    <R> R runWithStrategy(OperationWithResult<R> operation);
}
