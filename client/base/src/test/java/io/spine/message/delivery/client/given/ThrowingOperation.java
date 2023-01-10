/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client.given;

import io.spine.message.delivery.client.VoidOperation;

/**
 * An operation that always throws {@code RuntimeException}.
 */
public final class ThrowingOperation implements VoidOperation {

    /**
     * Always throws {@code RuntimeException}.
     */
    @Override
    public void run() {
        throw new RuntimeException("For testing purposes.");
    }
}
