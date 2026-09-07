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

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Disables {@link RequiresDeliveryImage}-annotated tests when they cannot run.
 *
 * <p>Two cases are recognized:
 * <ol>
 *     <li>a CI runner that cannot launch Docker containers at all, signalled by the
 *         {@code WINDOWS_CI_NO_DOCKER} environment variable;
 *     <li>the {@linkplain DeliveryImage#NAME server image} being absent from the local
 *         Docker daemon and not pullable from Artifact Registry — offline, or before
 *         the first publication. A local image, typically built from the working tree
 *         by {@code jibDockerBuild}, is never replaced by a pull.
 * </ol>
 *
 * <p>Docker's own presence is not probed here — the {@code checkDockerAvailable} Gradle
 * gate fails the build when it is missing. Mirrors {@code RequiresDockerCondition} of
 * the {@code :storage:redis} module.
 */
final class RequiresDeliveryImageCondition implements ExecutionCondition {

    /**
     * The environment variable a CI runner sets to mark itself unable to launch the Docker
     * container. Kept in sync with the {@code checkDockerAvailable} Gradle task.
     */
    private static final String WINDOWS_CI_NO_DOCKER = "WINDOWS_CI_NO_DOCKER";

    /** How long to wait for the {@code docker image inspect} probe. */
    private static final int PROBE_TIMEOUT_SECONDS = 30;

    /** How long to wait for {@code docker pull} of the server image. */
    private static final int PULL_TIMEOUT_SECONDS = 300;

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        var noDocker = Boolean.parseBoolean(System.getenv(WINDOWS_CI_NO_DOCKER));
        if (noDocker) {
            return ConditionEvaluationResult.disabled(
                    "Disabled on a CI runner (`WINDOWS_CI_NO_DOCKER`) that cannot launch the "
                            + "Docker container hosting the Delivery server.");
        }
        var image = "The `" + DeliveryImage.NAME + "` image ";
        if (!imagePresent() && !pullImage()) {
            return ConditionEvaluationResult.disabled(
                    image + "is not in the local Docker daemon, and pulling it from "
                            + "Artifact Registry failed. Build it with "
                            + "`./gradlew :delivery-server-cloud-run:jibDockerBuild` "
                            + "or retry `docker pull " + DeliveryImage.NAME + "`.");
        }
        return ConditionEvaluationResult.enabled(image + "is available.");
    }

    /** Tells whether the server image is present in the local Docker daemon. */
    private static boolean imagePresent() {
        return dockerSucceeds(PROBE_TIMEOUT_SECONDS, "image", "inspect", DeliveryImage.NAME);
    }

    /** Pulls the server image into the local Docker daemon, telling whether it succeeded. */
    private static boolean pullImage() {
        return dockerSucceeds(PULL_TIMEOUT_SECONDS, "pull", DeliveryImage.NAME);
    }

    /**
     * Runs {@code docker} with the given arguments, telling whether it exited successfully
     * within the timeout.
     *
     * <p>Any failure to even start the {@code docker} executable is treated as
     * "unsuccessful", which disables the test rather than failing it.
     */
    @SuppressWarnings("OverlyBroadCatchBlock" /* Any probe failure means "unavailable". */)
    private static boolean dockerSucceeds(int timeoutSeconds, String... args) {
        try {
            var process = new ProcessBuilder(dockerCommand(args))
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * The {@code docker} command with the given arguments, resolved for the current OS.
     *
     * <p>On Windows the call is routed through {@code cmd /c} so that the {@code docker}
     * executable is resolved via {@code PATH}/{@code PATHEXT} (i.e. {@code docker.exe} from
     * Docker Desktop); elsewhere {@code docker} is invoked directly.
     */
    private static List<String> dockerCommand(String... args) {
        var windows = "Windows";
        var onWindows = System.getProperty("os.name")
                              .regionMatches(true, 0, windows, 0, windows.length());
        var command = ImmutableList.<String>builder();
        if (onWindows) {
            command.add("cmd", "/c");
        }
        return command.add("docker").add(args).build();
    }
}
