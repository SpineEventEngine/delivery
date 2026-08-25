/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

@file:Suppress("RemoveRedundantQualifierName")

import com.google.protobuf.gradle.id
import io.spine.dependency.boms.BomsPlugin
import io.spine.dependency.build.ErrorProne
import io.spine.dependency.kotlinx.Coroutines
import io.spine.dependency.lib.ApacheHttp
import io.spine.dependency.lib.CommonsCodec
import io.spine.dependency.lib.GoogleApis
import io.spine.dependency.lib.Grpc
import io.spine.dependency.lib.Guava
import io.spine.dependency.lib.Jackson
import io.spine.dependency.lib.JacksonV2
import io.spine.dependency.lib.Kotlin
import io.spine.dependency.lib.PerfMark
import io.spine.dependency.lib.Slf4J
import io.spine.dependency.local.Base
import io.spine.dependency.local.BaseTypes
import io.spine.dependency.local.Change
import io.spine.dependency.local.Compiler
import io.spine.dependency.local.CoreJvm
import io.spine.dependency.local.Logging
import io.spine.dependency.local.Reflect
import io.spine.dependency.local.TestLib
import io.spine.dependency.local.Time
import io.spine.dependency.local.ToolBase
import io.spine.dependency.local.Validation
import io.spine.dependency.test.JUnit
import io.spine.gradle.checkstyle.CheckStyleConfig
import io.spine.gradle.github.pages.updateGitHubPages
import io.spine.gradle.javac.configureErrorProne
import io.spine.gradle.javac.configureJavac
import io.spine.gradle.javadoc.JavadocConfig
import io.spine.gradle.kotlin.applyJvmToolchain
import io.spine.gradle.kotlin.setFreeCompilerArgs
import io.spine.gradle.publish.IncrementGuard
import io.spine.gradle.publish.PublishingRepos
import io.spine.gradle.publish.spinePublishing
import io.spine.gradle.report.coverage.KoverConfig
import com.github.jk1.license.LicenseReportExtension
import io.spine.dependency.lib.KotlinPoet
import io.spine.gradle.report.license.LicenseReporter
import io.spine.gradle.report.pom.PomGenerator
import io.spine.gradle.repo.standardToSpineSdk
import io.spine.gradle.testing.configureLogging
import io.spine.gradle.testing.registerTestTasks
import org.gradle.jvm.tasks.Jar
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

buildscript {
    standardSpineSdkRepositories()

    doForceVersions(configurations)
    configurations {
        all {
            exclude(group = "io.spine", module = "spine-flogger-api")
            exclude(group = "io.spine", module = "spine-logging-backend")
            resolutionStrategy {
                val jackson = io.spine.dependency.lib.Jackson
                val logging = io.spine.dependency.local.Logging
                val cfg = this@all
                val rs = this@resolutionStrategy
                jackson.forceArtifacts(project, cfg, rs)
                io.spine.dependency.lib.JacksonV2.Core.forceArtifacts(project, cfg, rs)
                io.spine.dependency.lib.JacksonV2.DataType.forceArtifacts(project, cfg, rs)
                io.spine.dependency.lib.JacksonV2.DataFormat.forceArtifacts(project, cfg, rs)
                io.spine.dependency.lib.JacksonV2.Module.forceArtifacts(project, cfg, rs)
                io.spine.dependency.lib.JacksonV2.Junior.forceArtifacts(project, cfg, rs)
                io.spine.dependency.lib.Grpc.forceArtifacts(project, cfg, rs)
                force(
                    jackson.annotations,
                    jackson.bom,
                    io.spine.dependency.lib.Caffeine.lib,
                    io.spine.dependency.lib.CommonsIo.lib,
                    io.spine.dependency.lib.CommonsCompress.lib,
                    io.spine.dependency.lib.Kotlin.bom,
                    io.spine.dependency.local.Base.annotations,
                    io.spine.dependency.local.Base.lib,
                    io.spine.dependency.local.Base.environment,
                    io.spine.dependency.local.Base.format,
                    io.spine.dependency.local.Time.lib,
                    io.spine.dependency.local.Compiler.pluginLib,
                    logging.lib,
                    io.spine.dependency.local.Validation.runtime,
                )
            }
        }
    }

    dependencies {
        classpath(enforcedPlatform(io.spine.dependency.lib.Grpc.bom))
        classpath(enforcedPlatform(io.spine.dependency.kotlinx.Coroutines.bom))
        classpath(io.spine.dependency.local.Compiler.pluginLib)
        classpath(io.spine.dependency.local.CoreJvmCompiler.gradlePlugin)
    }
}

plugins {
    `java-library`
    kotlin("jvm")
    idea
    protobuf
    errorprone
    //`gradle-doctor`
}
apply<BomsPlugin>()

repositories.standardToSpineSdk()

spinePublishing {
    modules = setOf(
        // The `model` and `server` modules; their project names differ from
        // their directories (see `settings.gradle.kts`), producing
        // the `spine-delivery-model` and `spine-delivery-server` artifacts.
        "delivery-model",
        "delivery-server",
        // The client modules; their project names differ from their directories
        // (see `settings.gradle.kts`), producing the `spine-delivery-client` and
        // `spine-delivery-client-base` artifacts.
        "client:delivery-client",
        "client:delivery-client-base",
    )
    destinations = with(PublishingRepos) {
        setOf(
            // We publish only to GitHub because this project is proprietary and
            // is distributed via paid subscription.
            gitHub("delivery-server")
        )
    }
}

allprojects {
    apply {
        plugin("idea")
        plugin("project-report")
    }

    apply(from = "$rootDir/version.gradle.kts")
    group = "io.spine.delivery"
    version = extra["versionToPublish"]!!
}

subprojects {
    repositories.standardToSpineSdk()
    applyPlugins()
    forceConfigurations()

    // The CoreJvm compiler/ProtoData generates the Java message types and wires the
    // generated sources into the source sets, but not the gRPC service stubs. Configure the
    // `grpc` protoc plugin so the `*Grpc` classes are generated for the modules that declare
    // gRPC services (`grpc-api`, `server`).
    // `Grpc.ProtocPlugin.artifact` is the Java stub; `GrpcKotlin` is for Kotlin.
    @Suppress("DEPRECATION")
    configure<com.google.protobuf.gradle.ProtobufExtension> {
        plugins {
            id("grpc") {
                artifact = Grpc.ProtocPlugin.artifact
            }
        }
        generateProtoTasks {
            all().configureEach {
                plugins {
                    id("grpc")
                }
            }
        }
    }

    val javaVersion = BuildSettings.javaVersion
    setupJava(javaVersion)
    setupKotlin(javaVersion)

    defineDependencies()

    val generated = "$projectDir/generated"
    setupTestTasks()
    setupPublishing()
    configureTaskDependencies()
}

KoverConfig.applyTo(project)
PomGenerator.applyTo(project)
LicenseReporter.mergeAllReports(project)

/**
 * Applies plugins common to all modules to this subproject.
 */
fun Project.applyPlugins() {
    apply {
        plugin("java-library")
        // Coverage via Kover (JaCoCo engine through `useJacoco(...)`), aggregated
        // at the root by `KoverConfig`. Matches `io.spine.dependency.test.Kover.id`.
        plugin("org.jetbrains.kotlinx.kover")
        plugin("com.google.protobuf")
        plugin("net.ltgt.errorprone")
        plugin("kotlin")
        plugin("pmd")
        plugin("maven-publish")
        plugin("pmd-settings")
        plugin("dokka-setup")
        plugin("io.spine.core-jvm")
    }

    apply<IncrementGuard>()
    apply<BomsPlugin>()

    LicenseReporter.generateReportIn(project)
    // Scope each project's license report to itself. The jk1 plugin otherwise defaults to
    // `[project] + subprojects`, so the source-less `storage` grouping project would resolve
    // its subprojects' configurations (e.g. `:storage:base:checkstyle`) from its own task —
    // which Gradle 9 rejects under `org.gradle.parallel=true` ("Resolution of the configuration
    // ... was attempted without an exclusive lock"). `mergeAllLicenseReports` already aggregates
    // every subproject individually, so restricting each report to its own project keeps the
    // merged output identical while removing the cross-project resolution.
    project.the<LicenseReportExtension>().projects = arrayOf(project)
    JavadocConfig.applyTo(project)
    CheckStyleConfig.applyTo(project)
}

/**
 * Configures Java tasks in this project.
 */
fun Project.setupJava(javaVersion: JavaLanguageVersion) {
    java {
        toolchain.languageVersion.set(javaVersion)
    }
    tasks {
        withType<JavaCompile>().configureEach {
            configureJavac()
            configureErrorProne()
        }
        withType<Jar>().configureEach {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
    }
}

/**
 * Configures Kotlin tasks in this project.
 */
fun Project.setupKotlin(javaVersion: JavaLanguageVersion) {
    kotlin {
        applyJvmToolchain(javaVersion.asInt())
        explicitApi()
        compilerOptions {
            jvmTarget.set(BuildSettings.jvmTarget)
            setFreeCompilerArgs()
        }
    }
}

/**
 * Names of the modules whose tests need a Docker environment.
 *
 * Matched against `project.name` — the Gradle project name, which for several modules
 * differs from the directory (see `settings.gradle.kts`).
 *
 * Their `Test` tasks depend on [CheckDockerAvailable], so that an environment without
 * Docker cannot produce a misleading "tests passed" result.
 *
 * Declared as a function rather than a top-level `val` so that it is safe to call from
 * the `subprojects {}` configuration, which runs before a top-level property initializer
 * further down the script would have executed.
 */
fun dockerDependentModules() = setOf("redis", "delivery-client", "integration-test")

/**
 * Names of the modules whose tests additionally need the Delivery server *image*.
 *
 * Unlike [dockerDependentModules], a missing image is reported as a warning rather than
 * a build failure: the image lives in a private registry most developers cannot reach,
 * and the suites needing it skip themselves when it is absent (see
 * `RequiresDeliveryImage`). See [CheckDeliveryImageAvailable].
 *
 * Declared as a function for the same reason as [dockerDependentModules].
 */
fun imageDependentModules() = setOf("delivery-client", "integration-test")

/**
 * The Delivery server image the `integration`-tagged suites run against.
 *
 * Kept in sync with the `jib` configuration of `deployment/cloud-run/build.gradle.kts`
 * and with `DeliveryImage` of the `:fixtures` module, which probes for the same name.
 *
 * Declared as a function for the same reason as [dockerDependentModules].
 */
fun deliveryServerImage() = "gcr.io/spine-dev/simple-message-delivery-server:latest"

/**
 * Common base of the Docker-related gates, holding the `docker` probe.
 *
 * The probe lives here rather than at the script's top level because a task class cannot
 * reach script-scope declarations. `gcloud-jvm` solves this by duplicating the helper in
 * each gate; a shared base keeps one copy.
 */
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

    /** The name of the gated module, used in the failure message. */
    @get:Input
    abstract val moduleName: Property<String>

    @TaskAction
    fun check() {
        val module = moduleName.get()
        if (windowsCiWithoutDocker()) {
            logger.lifecycle(
                "Skipping the Docker requirement for `:$module`: `$WINDOWS_CI_NO_DOCKER` " +
                    "is set, so the Testcontainers tests are skipped on this runner."
            )
            return
        }
        if (dockerAvailable()) {
            return
        }
        throw GradleException(
            """
            No Docker environment is available, but the tests of `:$module` require one.

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

    /** The name of the module whose integration suites use the image. */
    @get:Input
    abstract val moduleName: Property<String>

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

            The `integration`-tagged tests of `:${moduleName.get()}` run the server from
            this image. Without it they are skipped, so the build can pass while verifying
            less than it appears to.

            Build the image locally to run them:

                ./gradlew :delivery-server-cloud-run:jibDockerBuild

            The image is otherwise hosted in the private `gcr.io/spine-dev` registry.
            """.trimIndent()
        )
    }
}

/**
 * Configures test tasks in this project.
 *
 * Docker-dependent tests are gated per module: [CheckDockerAvailable] fails the build
 * where Docker is required but missing, and [CheckDeliveryImageAvailable] warns where the
 * Delivery server image is required but missing.
 */
fun Project.setupTestTasks() {
    val dockerGate = name.takeIf { it in dockerDependentModules() }?.let { module ->
        tasks.register<CheckDockerAvailable>("checkDockerAvailable") {
            moduleName.set(module)
        }
    }
    val imageGate = name.takeIf { it in imageDependentModules() }?.let { module ->
        tasks.register<CheckDeliveryImageAvailable>("checkDeliveryImageAvailable") {
            moduleName.set(module)
            image.set(deliveryServerImage())
        }
    }
    tasks {
        registerTestTasks()
        test {
            useJUnitPlatform { includeEngines("junit-jupiter") }
            configureLogging()
        }
        listOfNotNull(dockerGate, imageGate).forEach { gate ->
            withType<Test>().configureEach {
                dependsOn(gate)
            }
        }
    }
}

/**
 * Defines dependencies of this subproject.
 */
fun Project.defineDependencies() {
    // The `storage` grouping project has no sources of its own — it only aggregates its
    // subprojects (`base`, `redis`, `hazelcast`) and compiles nothing. Declaring test
    // dependencies on such a source-less project is inert, and it contributes an
    // unversioned `junit-platform-launcher` (no test graph to resolve the JUnit BOM
    // against) that surfaces as a `null`-versus-`6.1.0` duplicate in the merged
    // dependency report. Skip dependency declarations for projects without sources.
    if (!file("src").exists()) {
        return
    }
    dependencies {
        ErrorProne.apply {
            errorprone(core)
        }
        implementation(CoreJvm.server)

        implementation(Validation.runtime)

        // Full JUnit Jupiter set (api + params + engine): delivery's tests use
        // parameterized tests (`org.junit.jupiter.params`), which gcloud-jvm (Kotest-based)
        // does not pull in.
        JUnit.Jupiter.modules.forEach { testImplementation(it) }
        // Put the JUnit Platform launcher on the test runtime classpath explicitly.
        // Gradle's auto-provisioned launcher is not pinned to the forced JUnit 6
        // platform here, which otherwise fails with "Failed to load JUnit Platform".
        testRuntimeOnly(JUnit.Platform.launcher)
        // Testcontainers logs through SLF4J. Provide a runtime SLF4J binding so the
        // container logs are emitted (and the "No SLF4J providers were found" warning
        // does not appear) when running the Datastore Emulator-based tests.
        testRuntimeOnly(Slf4J.simple)
        testImplementation(TestLib.lib)

        testImplementation(CoreJvm.serverTestLib)
        // Provides the generated test proto types and reusable test base classes
        // previously taken from the `spine-server` test (`:test`) classifier artifact.
        // The test fixtures are published under the `io.spine:server-test-fixtures`
        // capability, so we request that variant explicitly.
        testImplementation(CoreJvm.server) {
            capabilities {
                requireCapability("io.spine:server-test-fixtures")
            }
        }
    }
}

/**
 * Forces dependencies of this project.
 */
fun Project.forceConfigurations() {
    configurations {
        forceVersions()
        excludeProtobufLite()

        all {
            resolutionStrategy {
                val cfg = this@all
                val rs = this@resolutionStrategy
                /* The gRPC artifacts are version-less and get their versions from the
                   gRPC BOM. We force the whole set via `forceArtifacts()` and pin the BOM
                   to keep a single gRPC version across modules and the compiler plugins. */
                Grpc.forceArtifacts(project, cfg, rs)
                JacksonV2.Core.forceArtifacts(project, cfg, rs)
                JacksonV2.DataType.forceArtifacts(project, cfg, rs)
                JacksonV2.DataFormat.forceArtifacts(project, cfg, rs)
                JacksonV2.Module.forceArtifacts(project, cfg, rs)
                // The `google-cloud-*` libraries pull additional gRPC artifacts
                // (`grpc-alts`, `grpc-xds`, `grpc-grpclb`, `grpc-services`, etc.) at an
                // older version. Align the `io.grpc` group with the version defined by the
                // gRPC BOM forced above. The `grpc-kotlin-*` artifacts are versioned
                // independently (see `GrpcKotlin`), so they are left untouched.
                eachDependency {
                    if (requested.group == "io.grpc" && !requested.name.contains("kotlin")) {
                        useVersion(Grpc.version)
                    }
                }
                exclude("io.spine", "spine-validate")
                force(
                    Kotlin.bom,
                    KotlinPoet.lib,
                    Coroutines.bom,
                    JUnit.bom,
                    Jackson.annotations,
                    JacksonV2.bom,
                    Grpc.ProtocPlugin.artifact,
                    Grpc.bom,
                    Guava.lib,
                    Guava.testLib,

                    Base.lib,
                    Base.annotations,
                    Base.environment,
                    Base.format,
                    Reflect.lib,
                    Validation.runtime,
                    Time.lib,
                    Time.testLib,
                    Time.javaExtensions,
                    Logging.lib,
                    Logging.libJvm,
                    Logging.middleware,
                    Logging.grpcContext,
                    BaseTypes.lib,
                    Change.lib,
                    TestLib.lib,
                    ToolBase.pluginBase,
                    CoreJvm.server,
                    Compiler.api,
                    Compiler.pluginLib,
                    Compiler.gradleApi,
                    Compiler.params,

                    GoogleApis.AuthLibrary.credentials,
                    GoogleApis.AuthLibrary.oAuth2Http,
                    GoogleApis.commonProtos,
                    GoogleApis.common,

                    ApacheHttp.core,
                    CommonsCodec.lib,
                    PerfMark.api
                )
            }
        }
    }
}

/**
 * Configures publishing for this subproject.
 */
fun Project.setupPublishing() {
    updateGitHubPages {
        rootFolder.set(rootDir)
    }

    tasks.named("publish") {
        dependsOn("${project.path}:updateGitHubPages")
    }
}

