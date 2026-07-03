/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.client;

/**
 * Request that doesn't return value.
 */
public interface VoidRequest {

    /**
     * Performs an action meant by the request.
     */
    void run();
}
