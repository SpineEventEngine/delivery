/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.server.grpc;

/**
 * Exposes service health status.
 */
public interface HealthAware {

    /**
     * Returns {@code true} if the service is healthy, {@code false} otherwise.
     */
    default boolean healthy() {
        return true;
    }

    /**
     * Sets the service health status.
     */
    default void healthy(boolean value) {
        // do nothing.
    }
}
