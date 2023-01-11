/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client.strategy;

import com.google.common.collect.ImmutableList;
import io.spine.message.delivery.client.ExecutionFailedException;

/**
 * Strategy that doesn't handle errors but propagates them if the request failed.
 */
public final class Propagate extends AbstractExecutionStrategy {

    /**
     * Always throws {@code ExecutionFailedException}.
     */
    @Override
    protected void handleException(RuntimeException e) throws ExecutionFailedException {
        throw new ExecutionFailedException(ImmutableList.of(e));
    }
}
