import io.spine.dependency.local.CoreJvm
import io.spine.dependency.test.Testcontainers
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import org.gradle.process.ExecOperations

dependencies {
    api(CoreJvm.server)
    implementation(project(":storage:base"))
    // `Redisson` is not part of the shared `config` dependency catalog; declared inline.
    implementation("org.redisson:redisson:3.16.3")
    testImplementation(Testcontainers.lib)
    testImplementation(project(path = ":storage:base", configuration = "testArtifacts"))
}

/**
 * Fails the build unless a Docker environment is available for the Testcontainers-based
 * Redis tests (`RedisRecordStorageTest`, `MultitenantStorageTest`), which start a
 * `redis:6-alpine` container.
 *
 * Without Docker these suites verify nothing, so the build fails here instead of passing
 * silently. The sole exemption is a CI runner that sets `WINDOWS_CI_NO_DOCKER` because it
 * cannot launch the Linux container; there the gate passes and the tests are skipped by the
 * `RequiresDocker` JUnit condition, which reads the same variable.
 *
 * Mirrors the `CheckDockerAvailable` gate in the `gcloud-jvm` repository.
 */
abstract class CheckDockerAvailable : DefaultTask() {

    @get:Inject
    abstract val execOperations: ExecOperations

    private companion object {

        /**
         * The environment variable a CI runner sets to signal that it cannot launch the
         * Docker container. Kept in sync with `RequiresDockerCondition`, which reads the same
         * variable to skip the tests there.
         */
        const val WINDOWS_CI_NO_DOCKER = "WINDOWS_CI_NO_DOCKER"
    }

    @TaskAction
    fun check() {
        if (System.getenv(WINDOWS_CI_NO_DOCKER).toBoolean()) {
            logger.lifecycle(
                "Skipping the Docker requirement for `:storage:redis`: `$WINDOWS_CI_NO_DOCKER` " +
                    "is set, so the Redis Testcontainers tests are skipped on this runner."
            )
            return
        }
        if (dockerAvailable()) {
            return
        }
        throw GradleException(
            """
            No Docker environment is available, but the tests of `:storage:redis` require one.

            These tests exercise a Redis server running inside a Docker container
            (Testcontainers). Without Docker they verify nothing, so the build fails here
            instead of passing silently. Install Docker (or start the Docker daemon) and run
            the build again.

            The only exemption is a CI runner that sets `$WINDOWS_CI_NO_DOCKER` (it cannot
            launch the Linux container); there this gate passes and the tests are skipped by
            the `RequiresDocker` JUnit condition.
            """.trimIndent()
        )
    }

    /**
     * Tells whether `docker info` reports a reachable Docker daemon.
     *
     * Any failure to even start the `docker` executable (for example, it is not installed) is
     * treated as "no Docker available".
     */
    private fun dockerAvailable(): Boolean = try {
        val sink = ByteArrayOutputStream()
        val result = execOperations.exec {
            commandLine(dockerInfoCommand())
            standardOutput = sink
            errorOutput = sink
            isIgnoreExitValue = true
        }
        result.exitValue == 0
    } catch (_: Exception) {
        false
    }

    /**
     * The `docker info` probe, resolved for the current OS.
     *
     * On Windows the check is routed through `cmd /c` so that the `docker` executable is
     * resolved via `PATH`/`PATHEXT` (i.e. `docker.exe` from Docker Desktop); elsewhere
     * `docker` is invoked directly.
     */
    private fun dockerInfoCommand(): List<String> {
        val onWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
        return if (onWindows) {
            listOf("cmd", "/c", "docker", "info")
        } else {
            listOf("docker", "info")
        }
    }
}

val checkDockerAvailable = tasks.register<CheckDockerAvailable>("checkDockerAvailable")

tasks.withType<Test>().configureEach {
    dependsOn(checkDockerAvailable)
}
