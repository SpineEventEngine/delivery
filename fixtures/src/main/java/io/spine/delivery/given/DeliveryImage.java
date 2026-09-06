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

package io.spine.delivery.given;

import org.testcontainers.utility.DockerImageName;

/**
 * The Delivery server image that the {@code integration}-tagged suites run against.
 */
public final class DeliveryImage {

    /**
     * The name of the image.
     *
     * <p>Kept in sync with the {@code jib} configuration of
     * {@code deployment/cloud-run/build.gradle.kts} and with {@code DELIVERY_SERVER_IMAGE}
     * of {@code buildSrc/src/main/kotlin/DockerGates.kt}, which the {@code module}
     * script plugin passes to the {@code checkDeliveryImageAvailable} gate that warns
     * when the image is missing.
     */
    public static final String NAME = "gcr.io/spine-dev/delivery-server:latest";

    /** Prevents instantiation of this utility class. */
    private DeliveryImage() {
    }

    /**
     * Returns the image name as a Testcontainers {@code DockerImageName}.
     */
    public static DockerImageName dockerImageName() {
        return DockerImageName.parse(NAME);
    }
}
