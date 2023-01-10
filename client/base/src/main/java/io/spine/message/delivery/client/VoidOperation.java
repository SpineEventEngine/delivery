/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

/**
 * An operation that doesn't return value.
 */
public interface VoidOperation {

    /**
     * Performs an action meant by the operation.
     */
    void run();
}
