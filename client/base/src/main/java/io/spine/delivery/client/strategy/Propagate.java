/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.client.strategy;

/**
 * Strategy that doesn't handle errors but propagates them if the request failed.
 */
public final class Propagate extends AbstractExecutionStrategy {

    @Override
    protected <R> ActionWithResult<R> handleException(FailedRequest<R> failure) {
        return failure.propagate();
    }

    @Override
    protected Action handleException(FailedVoidRequest failure) {
        return failure.propagate();
    }
}
