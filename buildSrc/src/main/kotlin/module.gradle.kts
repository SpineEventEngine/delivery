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

@file:Suppress("AvoidApplyPluginMethod") // Some plugins are applied by ID at runtime.

import com.github.jk1.license.LicenseReportExtension
import com.google.protobuf.gradle.id
import io.spine.dependency.boms.BomsPlugin
import io.spine.dependency.build.ErrorProne
import io.spine.dependency.kotlinx.AtomicFu
import io.spine.dependency.kotlinx.Coroutines
import io.spine.dependency.lib.ApacheHttp
import io.spine.dependency.lib.Caffeine
import io.spine.dependency.lib.CommonsCodec
import io.spine.dependency.lib.GoogleApis
import io.spine.dependency.lib.Grpc
import io.spine.dependency.lib.Guava
import io.spine.dependency.lib.Jackson
import io.spine.dependency.lib.JacksonV2
import io.spine.dependency.lib.Kotlin
import io.spine.dependency.lib.KotlinPoet
import io.spine.dependency.lib.PerfMark
import io.spine.dependency.lib.Protobuf
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
import io.spine.gradle.SpineTaskGroup
import io.spine.gradle.checkstyle.CheckStyleConfig
import io.spine.gradle.github.pages.updateGitHubPages
import io.spine.gradle.javac.configureErrorProne
import io.spine.gradle.javac.configureJavac
import io.spine.gradle.javadoc.JavadocConfig
import io.spine.gradle.kotlin.applyJvmToolchain
import io.spine.gradle.kotlin.setFreeCompilerArgs
import io.spine.gradle.publish.IncrementGuard
import io.spine.gradle.report.license.LicenseReporter
import io.spine.gradle.testing.configureLogging
import io.spine.gradle.testing.registerTestTasks
import org.gradle.jvm.tasks.Jar

plugins {
    `java-library`
    kotlin("jvm")
    // Coverage via Kover (JaCoCo engine through `useJacoco(...)`), aggregated
    // at the root by `KoverConfig`. Matches `io.spine.dependency.test.Kover.id`.
    id("org.jetbrains.kotlinx.kover")
    id("com.google.protobuf")
    id("net.ltgt.errorprone")
    pmd
    `maven-publish`
    id("pmd-settings")
    id("dokka-setup")
}

// The CoreJvm compiler plugin comes from the root `buildscript` classpath rather than
// from `buildSrc`, so it cannot be requested in the `plugins` block above.
apply(plugin = "io.spine.core-jvm")

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

typealias Module = Project

project.run {
    forceConfigurations()
    setupProtobuf()

    val javaVersion = BuildSettings.javaVersion
    setupJava(javaVersion)
    setupKotlin(javaVersion)

    defineDependencies()

    setupTestTasks()
    setupPublishing()
    configureTaskDependencies()
}

/**
 * Configures the `grpc` protoc plugin for this module.
 *
 * The CoreJvm compiler/ProtoData generates the Java message types and wires the
 * generated sources into the source sets, but not the gRPC service stubs. Configure the
 * `grpc` protoc plugin so the `*Grpc` classes are generated for the modules that declare
 * gRPC services (`grpc-api`, `server`).
 * `Grpc.ProtocPlugin.artifact` is the Java stub; `GrpcKotlin` is for Kotlin.
 */
@Suppress("DEPRECATION")
fun Module.setupProtobuf() {
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
}

/**
 * Configures Java tasks in this project.
 */
fun Module.setupJava(javaVersion: JavaLanguageVersion) {
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
fun Module.setupKotlin(javaVersion: JavaLanguageVersion) {
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
 * Docker-dependent tests are gated per module: [CheckDockerAvailable] fails the build
 * where Docker is required but missing, and [CheckDeliveryImageAvailable] warns where the
 * Delivery server image is required but missing.
 */
fun Module.setupTestTasks() {
    val dockerGate = name.takeIf { it in dockerDependentModules }?.let { module ->
        tasks.register<CheckDockerAvailable>("checkDockerAvailable") {
            group = SpineTaskGroup.name
            description = "Fails the build unless a Docker environment is available."
            moduleName.set(module)
        }
    }
    val imageGate = name.takeIf { it in imageDependentModules }?.let { module ->
        tasks.register<CheckDeliveryImageAvailable>("checkDeliveryImageAvailable") {
            group = SpineTaskGroup.name
            description = "Warns unless the Delivery server image is in the local daemon."
            moduleName.set(module)
            image.set(DELIVERY_SERVER_IMAGE)
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
fun Module.defineDependencies() {
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
fun Module.forceConfigurations() {
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
                    AtomicFu.lib,
                    Jackson.bom,
                    Protobuf.javaLib,
                    Caffeine.lib,
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
fun Module.setupPublishing() {
    updateGitHubPages {
        rootFolder.set(rootDir)
    }

    tasks.named("publish") {
        dependsOn("${project.path}:updateGitHubPages")
    }
}
