/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

/**
 * An operation that has result after execution.
 *
 * @param <R>
 *         type of the result.
 */
public interface OperationWithResult<R> {

    /**
     * Performs an action meant by the operation and returns the result.
     */
    R run();
}
