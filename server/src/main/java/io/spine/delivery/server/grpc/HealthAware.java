/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
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
        // Do nothing.
    }
}
