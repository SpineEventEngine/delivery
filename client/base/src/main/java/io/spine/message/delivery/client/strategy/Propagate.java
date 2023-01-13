/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client.strategy;

import com.google.common.collect.ImmutableList;
import io.spine.message.delivery.client.ExecutionFailedException;

import java.util.function.Supplier;

/**
 * Strategy that doesn't handle errors but propagates them if the request failed.
 */
public final class Propagate extends AbstractExecutionStrategy {

    @Override
    protected <R> Supplier<R> handleException(Exception e, Supplier<R> operation) {
        throw new ExecutionFailedException(ImmutableList.of(e));
    }

    @Override
    protected Runnable handleException(Exception e, Runnable operation) {
        throw new ExecutionFailedException(ImmutableList.of(e));
    }
}
