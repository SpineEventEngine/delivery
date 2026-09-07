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
