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
