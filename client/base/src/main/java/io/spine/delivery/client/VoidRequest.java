/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.client;

/**
 * A request that doesn't return a value.
 */
public interface VoidRequest {

    /**
     * Performs an action meant by the request.
     */
    void run();
}
