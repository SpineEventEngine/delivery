/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.server.storage.redis;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Disables {@link RequiresDocker}-annotated tests on a CI runner that cannot launch Docker
 * containers, signalled by the {@code WINDOWS_CI_NO_DOCKER} environment variable.
 *
 * <p>This condition does not probe Docker; enforcing Docker's presence is the job of the
 * {@code checkDockerAvailable} Gradle task, which reads the same variable. Mirrors
 * {@code EmulatorCondition} in the {@code gcloud-jvm} repository.
 */
final class RequiresDockerCondition implements ExecutionCondition {

    /**
     * The environment variable a CI runner sets to mark itself unable to launch the Docker
     * container. Kept in sync with the {@code checkDockerAvailable} Gradle task.
     */
    private static final String WINDOWS_CI_NO_DOCKER = "WINDOWS_CI_NO_DOCKER";

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        boolean noDocker = Boolean.parseBoolean(System.getenv(WINDOWS_CI_NO_DOCKER));
        if (noDocker) {
            return ConditionEvaluationResult.disabled(
                    "Disabled on a CI runner (`WINDOWS_CI_NO_DOCKER`) that cannot launch the "
                            + "Docker container hosting Redis.");
        }
        return ConditionEvaluationResult.enabled(
                "Docker is expected to be available for the Redis Testcontainers test.");
    }
}
