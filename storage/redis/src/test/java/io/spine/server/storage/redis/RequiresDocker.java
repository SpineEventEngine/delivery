/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.server.storage.redis;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Testcontainers-based test class that requires a Docker environment.
 *
 * <p>Such tests are skipped only on a CI runner that sets the {@code WINDOWS_CI_NO_DOCKER}
 * environment variable, which cannot launch the Linux container hosting Redis. Everywhere
 * else Docker is required, and its presence is enforced by the {@code checkDockerAvailable}
 * Gradle task before the test task runs.
 *
 * <p>Mirrors {@code @EmulatorTest}/{@code EmulatorCondition} in the {@code gcloud-jvm} repository.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ExtendWith(RequiresDockerCondition.class)
@interface RequiresDocker {
}
