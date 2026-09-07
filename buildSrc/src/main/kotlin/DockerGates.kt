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

import java.io.ByteArrayOutputStream
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault

/**
 * The Docker-related gates guarding this repository's Testcontainers suites, and the
 * lists of the modules they apply to.
 *
 * The `module` script plugin registers these tasks; they live here because a precompiled
 * script plugin cannot declare classes.
 *
 * Like `DsBuildExtensions.kt`, this file is owned by this repository rather than by
 * the `config` module: `./config/pull` overlays `buildSrc` with the files `config`
 * distributes, and preserves only `module.gradle.kts`. Should `config` ever start
 * shipping a file of this name, the two would collide silently — the build would keep
 * compiling while gating the wrong modules.
 */
private const val ABOUT = ""

/**
 * Names of the modules whose tests need a Docker environment.
 *
 * Matched against `project.name` — the Gradle project name, which for several modules
 * differs from the directory (see `settings.gradle.kts`).
 *
 * Their `Test` tasks depend on [CheckDockerAvailable], so that an environment without
 * Docker cannot produce a misleading "tests passed" result.
 */
val dockerDependentModules = setOf("redis", "delivery-client", "integration-test")

/**
 * Names of the modules whose tests additionally need the Delivery server *image*.
 *
 * Unlike [dockerDependentModules], a missing image is reported as a warning rather than
 * a build failure: the image lives in a private registry most developers cannot reach,
 * and the suites needing it skip themselves when it is absent (see
 * `RequiresDeliveryImage`). See [CheckDeliveryImageAvailable].
 */
val imageDependentModules = setOf("delivery-client", "integration-test")

/**
 * The Delivery server image the `integration`-tagged suites run against.
 *
 * Kept in sync with the `jib` configuration of `deployment/cloud-run/build.gradle.kts`
 * and with `DeliveryImage` of the `:fixtures` module, which probes for the same name.
 */
const val DELIVERY_SERVER_IMAGE = "gcr.io/spine-dev/delivery-server:latest"

/**
 * Common base of the Docker-related gates, holding the `docker` probe.
 *
 * `gcloud-jvm` duplicates this helper in each gate; a shared base keeps one copy.
 */
@DisableCachingByDefault(because = "Probes the local Docker daemon, which is not an input.")
abstract class DockerGate : DefaultTask() {

    @get:Inject
    abstract val execOperations: ExecOperations

    protected companion object {

        /**
         * The environment variable a CI runner sets to mark itself unable to launch Docker
         * containers.
         *
         * Kept in sync with `RequiresDockerCondition` and `RequiresDeliveryImageCondition`,
         * which read the same variable to skip the affected tests there.
         */
        const val WINDOWS_CI_NO_DOCKER = "WINDOWS_CI_NO_DOCKER"
    }

    /** Tells whether this runner declared itself unable to launch Docker containers. */
    protected fun windowsCiWithoutDocker(): Boolean =
        System.getenv(WINDOWS_CI_NO_DOCKER).toBoolean()

    /**
     * Tells whether `docker info` reports a reachable Docker daemon.
     *
     * Any failure to even start the `docker` executable (for example, it is not installed)
     * is treated as "no Docker available".
     */
    protected fun dockerAvailable(): Boolean = dockerSucceeds("info")

    /** Tells whether the given image is present in the local Docker daemon. */
    protected fun imagePresent(image: String): Boolean =
        dockerSucceeds("image", "inspect", image)

    /**
     * Runs `docker` with the given arguments, reporting whether it exited successfully.
     *
     * On Windows the call is routed through `cmd /c` so that the `docker` executable is
     * resolved via `PATH`/`PATHEXT` (i.e. `docker.exe` from Docker Desktop); a bare program
     * name is not reliably resolved otherwise. Elsewhere `docker` is invoked directly.
     */
    private fun dockerSucceeds(vararg args: String): Boolean = try {
        val onWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
        val command =
            if (onWindows) listOf("cmd", "/c", "docker") + args
            else listOf("docker") + args
        val sink = ByteArrayOutputStream()
        val result = execOperations.exec {
            commandLine(command)
            standardOutput = sink
            errorOutput = sink
            isIgnoreExitValue = true
        }
        result.exitValue == 0
    } catch (_: Exception) {
        false
    }
}

/**
 * Fails the build unless a Docker environment is available for the Testcontainers-based
 * tests of the [Docker-dependent modules][dockerDependentModules].
 *
 * Without Docker these suites verify nothing, so the build fails here instead of passing
 * silently. The sole exemption is a CI runner that sets `WINDOWS_CI_NO_DOCKER` because it
 * cannot launch the Linux containers; there the gate passes and the tests are skipped by
 * their JUnit conditions, which read the same variable.
 *
 * Mirrors the `CheckDockerAvailable` gate in the `gcloud-jvm` repository.
 */
abstract class CheckDockerAvailable : DockerGate() {

    /** The path of the gated module, used in the failure message. */
    @get:Input
    abstract val modulePath: Property<String>

    @TaskAction
    fun check() {
        val module = modulePath.get()
        if (windowsCiWithoutDocker()) {
            logger.lifecycle(
                "Skipping the Docker requirement for `$module`: `$WINDOWS_CI_NO_DOCKER` " +
                        "is set, so the Testcontainers tests are skipped on this runner."
            )
            return
        }
        if (dockerAvailable()) {
            return
        }
        throw GradleException(
            """
            No Docker environment is available, but the tests of `$module` require one.

            These tests exercise services running inside Docker containers
            (Testcontainers). Without Docker they verify nothing, so the build fails here
            instead of passing silently. Install Docker (or start the Docker daemon) and
            run the build again.

            The only exemption is a CI runner that sets `$WINDOWS_CI_NO_DOCKER` (it cannot
            launch the Linux containers); there this gate passes and the tests are skipped
            by their JUnit conditions.
            """.trimIndent()
        )
    }
}

/**
 * Warns when the Delivery server image is missing from the local Docker daemon.
 *
 * The `integration`-tagged suites of the [image-dependent modules][imageDependentModules]
 * run the server from that image. Without it they skip themselves, so the build can pass
 * while verifying less than it appears to; this gate restores a visible signal.
 *
 * It only warns — see [imageDependentModules] for why a missing image is not a build
 * failure. Mirrors `CheckCredentialsAvailable` in the `gcloud-jvm` repository.
 */
abstract class CheckDeliveryImageAvailable : DockerGate() {

    /** The path of the module whose integration suites use the image. */
    @get:Input
    abstract val modulePath: Property<String>

    /** The image the suites run against. */
    @get:Input
    abstract val image: Property<String>

    @TaskAction
    fun check() {
        if (windowsCiWithoutDocker()) {
            return
        }
        val image = image.get()
        if (imagePresent(image)) {
            return
        }
        logger.warn(
            """

            WARNING: the Delivery server image `$image` is not in the local Docker daemon.

            The `integration`-tagged tests of `${modulePath.get()}` run the server from
            this image. Without it they are skipped, so the build can pass while verifying
            less than it appears to.

            Build the image locally to run them:

                ./gradlew :delivery-server-cloud-run:jibDockerBuild

            The image is otherwise hosted in the private `gcr.io/spine-dev` registry.
            """.trimIndent()
        )
    }
}
