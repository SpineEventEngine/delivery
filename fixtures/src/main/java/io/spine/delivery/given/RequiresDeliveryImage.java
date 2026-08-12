/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.given;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Testcontainers-based test class that runs the Delivery server from
 * the {@linkplain DeliveryImage#NAME server image}.
 *
 * <p>Such tests are skipped when the image is absent from the local Docker daemon, or on
 * a CI runner that sets {@code WINDOWS_CI_NO_DOCKER} and thus cannot launch Linux
 * containers. Build the image with {@code ./gradlew :delivery-server-cloud-run:jibDockerBuild}
 * to run them locally.
 *
 * <p>Docker itself is not probed here — that is the job of the {@code checkDockerAvailable}
 * Gradle gate, which fails the build outright. A missing image only warns, via
 * {@code checkDeliveryImageAvailable}, because the image lives in a private registry.
 *
 * <p>Mirrors {@code RequiresDocker} of the {@code :storage:redis} module.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ExtendWith(RequiresDeliveryImageCondition.class)
public @interface RequiresDeliveryImage {
}
