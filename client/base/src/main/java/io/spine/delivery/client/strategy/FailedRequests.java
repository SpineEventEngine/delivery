/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.client.strategy;

import com.google.common.collect.ImmutableList;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Utilities for working with {@link FailedRequest} and {@link FailedVoidRequest}.
 */
final class FailedRequests {

    /**
     * Prevents instantiation of this utility class.
     */
    private FailedRequests() {
    }

    /**
     * Ensures that the given {@code allExceptions} list is not empty.
     */
    static void checkHasExceptions(ImmutableList<RuntimeException> allExceptions) {
        checkArgument(!allExceptions.isEmpty(), "The exception list should not be empty.");
    }
}
