/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.client;

/**
 * A request that has a result after execution.
 *
 * @param <R>
 *         type of the result
 */
public interface RequestWithResult<R> {

    /**
     * Performs an action meant by the request and returns the result.
     */
    R evaluate();
}
