/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client.strategy;

/**
 * An operation that is applied when {@code RequestWithResult} failed.
 *
 * @param <R>
 *         type of result that have to be returned by the {@code RequestWithResult}. This
 *         action can return a result instead of the request in case of request failure.
 */
interface ActionWithResult<R> {

    /**
     * Executes the action.
     */
    R execute();
}
