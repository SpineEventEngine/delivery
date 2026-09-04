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

@file:Suppress("RemoveRedundantQualifierName")

import io.spine.dependency.boms.BomsPlugin
import io.spine.gradle.publish.PublishingRepos
import io.spine.gradle.publish.spinePublishing
import io.spine.gradle.repo.standardToSpineSdk
import io.spine.gradle.report.coverage.KoverConfig
import io.spine.gradle.report.license.LicenseReporter
import io.spine.gradle.report.pom.PomGenerator

buildscript {
    standardSpineSdkRepositories()

    doForceVersions(configurations)
    configurations {
        all {
            exclude(group = "io.spine", module = "spine-flogger-api")
            exclude(group = "io.spine", module = "spine-logging-backend")
            resolutionStrategy {
                val jackson = io.spine.dependency.lib.Jackson
                val base = io.spine.dependency.local.Base
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
                    io.spine.dependency.lib.JacksonV2.bom,
                    io.spine.dependency.lib.Caffeine.lib,
                    // Floor artifacts request the pre-refresh versions.
                    io.spine.dependency.kotlinx.Coroutines.bom,
                    io.spine.dependency.kotlinx.AtomicFu.lib,
                    // The refreshed compiler pins the current Time while
                    // floor artifacts still request the previous one.
                    io.spine.dependency.local.Time.lib,
                    io.spine.dependency.local.Time.javaExtensions,
                    // The Protobuf runtime must never be older than the
                    // refreshed gencode.
                    io.spine.dependency.lib.Protobuf.javaLib,
                    io.spine.dependency.lib.CommonsIo.lib,
                    io.spine.dependency.lib.CommonsCompress.lib,
                    io.spine.dependency.lib.Kotlin.bom,
                    base.annotations,
                    base.lib,
                    base.environment,
                    base.format,
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
            gitHub("delivery"),
            cloudArtifactRegistry
        )
    }
}

allprojects {
    apply {
        plugin("project-report")
    }

    apply(from = "$rootDir/version.gradle.kts")
    group = "io.spine.delivery"
    version = extra["versionToPublish"]!!
    // Declared here rather than in the `module` plugin so that the repositories exist
    // before any module-level plugin is applied. `BomsPlugin` in particular needs them
    // to resolve its BOM platforms; applying it against a repository-less project
    // silently drops the version alignment it provides.
    repositories.standardToSpineSdk()
}

// Per-module configuration lives in the `module` script plugin
// (`buildSrc/src/main/kotlin/module.gradle.kts`), which every subproject applies.

KoverConfig.applyTo(project)
PomGenerator.applyTo(project)
LicenseReporter.mergeAllReports(project)
