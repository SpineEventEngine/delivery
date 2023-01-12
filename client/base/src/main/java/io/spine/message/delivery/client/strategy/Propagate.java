/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client.strategy;

/**
 * Strategy that doesn't handle errors but propagates them if the request failed.
 */
public final class Propagate extends AbstractExecutionStrategy {

    /**
     * Always makes the strategy to stop execution and throw {@code ExecutionFailedException}.
     */
    @Override
    protected Decision handleException(RuntimeException e) {
        return Decision.STOP_AND_THROW;
    }
}
