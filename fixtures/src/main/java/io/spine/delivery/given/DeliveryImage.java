/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
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
     * of the root {@code build.gradle.kts}, whose {@code checkDeliveryImageAvailable} gate
     * warns when the image is missing.
     */
    public static final String NAME = "gcr.io/spine-dev/simple-message-delivery-server:latest";

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
