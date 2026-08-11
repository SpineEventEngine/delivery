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
import io.spine.gradle.report.license.LicenseReporter
import io.spine.gradle.report.pom.PomGenerator
import io.spine.gradle.repo.standardToSpineSdk
import io.spine.gradle.testing.configureLogging
import io.spine.gradle.testing.registerTestTasks
import org.gradle.jvm.tasks.Jar

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
                io.spine.dependency.lib.Jackson.DataType.forceArtifacts(project, cfg, rs)
                io.spine.dependency.lib.Jackson.DataFormat.forceArtifacts(project, cfg, rs)
                io.spine.dependency.lib.JacksonV2.Core.forceArtifacts(project, cfg, rs)
                io.spine.dependency.lib.JacksonV2.DataType.forceArtifacts(project, cfg, rs)
                io.spine.dependency.lib.JacksonV2.DataFormat.forceArtifacts(project, cfg, rs)
                io.spine.dependency.lib.JacksonV2.Module.forceArtifacts(project, cfg, rs)
                io.spine.dependency.lib.Grpc.forceArtifacts(project, cfg, rs)
                force(
                    jackson.annotations,
                    jackson.bom,
                    io.spine.dependency.lib.Grpc.bom,
                    io.spine.dependency.lib.Guava.lib,
                    io.spine.dependency.lib.Kotlin.bom,
                    io.spine.dependency.local.Base.annotations,
                    io.spine.dependency.local.Base.lib,
                    io.spine.dependency.local.Base.environment,
                    io.spine.dependency.local.Base.format,
                    io.spine.dependency.local.Reflect.lib,
                    io.spine.dependency.local.Time.lib,
                    io.spine.dependency.local.Time.javaExtensions,
                    io.spine.dependency.local.Compiler.api,
                    io.spine.dependency.local.Compiler.pluginLib,
                    io.spine.dependency.local.Compiler.gradleApi,
                    io.spine.dependency.local.Compiler.params,
                    io.spine.dependency.local.ToolBase.lib,
                    io.spine.dependency.local.CoreJvm.server,
                    logging.lib,
                    logging.libJvm,
                    logging.grpcContext,
                    io.spine.dependency.local.Validation.runtime,
                )
            }
        }
    }

    dependencies {
        classpath(enforcedPlatform(io.spine.dependency.lib.Grpc.bom))
        classpath(enforcedPlatform(io.spine.dependency.kotlinx.Coroutines.bom))
        classpath(io.spine.dependency.local.Compiler.pluginLib)
        classpath(io.spine.dependency.local.CoreJvmCompiler.pluginLib)
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
        "simple-server",
        // The `model` module; its project name differs from its directory
        // (see `settings.gradle.kts`), producing the `spine-delivery-model` artifact.
        "delivery-model",
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
    // gRPC services (`grpc-api`, `simple-server`).
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
 * Configures test tasks in this project.
 *
 * Docker-dependent tests are gated per module — see `storage/redis/build.gradle.kts`,
 * whose `CheckDockerAvailable` task fails the build when the Redis Testcontainers
 * tests cannot run.
 */
fun Project.setupTestTasks() {
    tasks {
        registerTestTasks()
        test {
            useJUnitPlatform { includeEngines("junit-jupiter") }
            configureLogging()
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
                Jackson.forceArtifacts(project, cfg, rs)
                Jackson.DataType.forceArtifacts(project, cfg, rs)
                Jackson.DataFormat.forceArtifacts(project, cfg, rs)
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
                    Coroutines.bom,
                    JUnit.bom,
                    Jackson.annotations,
                    Jackson.bom,
                    JacksonV2.bom,
                    Grpc.ProtocPlugin.artifact,
                    Grpc.bom,
                    Guava.lib,
                    Guava.testLib,
                    // The `proto-google-cloud-*` libraries bring an older `failureaccess`
                    // than the one used by the forced Guava version above.
                    "com.google.guava:failureaccess:1.0.3",
                    JUnit.legacy,

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
                    ToolBase.lib,
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

