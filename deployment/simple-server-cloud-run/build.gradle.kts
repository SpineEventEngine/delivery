/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.spine.dependency.lib.Grpc
import io.spine.dependency.lib.Log4j2
import io.spine.dependency.lib.Micronaut
import io.spine.dependency.local.Logging
import io.spine.dependency.storage.Hazelcast
import io.spine.dependency.storage.Redisson

plugins {
    application
    jib
    id("com.gradleup.shadow")
}

// This module resolves the Micronaut graph via `:admin-server`, so it needs the
// same version alignment under `failOnVersionConflict()`.
// See the KDoc of `alignMicronautPlatform()` in `buildSrc`.
alignMicronautPlatform()

// The fat-JAR classpath is the first place where the graph of `simple-server`
// (Redisson, Hazelcast, Log4j2) meets the Micronaut platform of `admin-server`.
// Under `failOnVersionConflict()` each cross-graph disagreement is settled
// explicitly below, taking the newest version requested in the merged graph.
configurations.all {
    resolutionStrategy.eachDependency {
        when (requested.group) {
            // Micronaut's `netty-bom` pins a newer Netty line than Redisson requests.
            // The `netty-tcnative-*` artifacts follow their own versioning scheme,
            // so they are left alone.
            "io.netty" -> if (!requested.name.contains("tcnative")) {
                useVersion(Micronaut.nettyVersion)
            }
            // Pinned by the Micronaut platform; Redisson requests older patches.
            // `reactor-bom` is versioned by release train, not by core version.
            "io.projectreactor" -> if (!requested.name.endsWith("-bom")) {
                useVersion(Micronaut.reactorCoreVersion)
            }
            "io.reactivex.rxjava3" -> useVersion(Micronaut.rxJavaVersion)
            // This build's Log4j2 is newer than the Micronaut platform's pin.
            "org.apache.logging.log4j" -> useVersion(Log4j2.version)
            // Redisson requests a newer SnakeYAML than the Micronaut platform pins.
            "org.yaml" -> useVersion(Redisson.snakeYamlVersion)
            // Redisson requests a newer Byte Buddy than the Micronaut test BOM pins.
            "net.bytebuddy" -> useVersion(Redisson.byteBuddyVersion)
            // `storage:hazelcast` uses a newer Hazelcast than the platform's pin.
            "com.hazelcast" -> useVersion(Hazelcast.version)
        }
    }
}

dependencies {
    runtimeOnly(Grpc.nettyShaded)
    runtimeOnly(Log4j2.core)
    // Routes the SLF4J calls of Micronaut to the Log4j2 backend above.
    runtimeOnly(Log4j2.slf4j2Bridge)
    runtimeOnly(Logging.log4j2Backend)
    implementation(project(":simple-server"))
    implementation(project(":admin-server"))
    implementation(project(":admin-ui"))
}

val appClassName = "io.spine.delivery.launcher.Launcher"

application {
    mainClass.set(appClassName)
    applicationDefaultJvmArgs = listOf(
        "-Xdebug", "-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=7007"
    )
}

tasks.withType<ShadowJar> {
    mergeServiceFiles()
    mergeServiceFiles("desc.ref")
    manifest {
        attributes["Multi-Release"] = "true" // https://github.com/johnrengelman/shadow/issues/449
        attributes["Main-Class"] = appClassName
    }
}

/**
 * The name under which the target GCP project is passed to the build.
 *
 * Shared by all three lookups below, so that a Gradle project property, a system
 * property, and an environment variable are all spelled the same way.
 */
val gcpProjectKey = "GCP_PROJECT"

/**
 * The GCP project to publish the container image to, when no project is given.
 */
val defaultGcpProject = "spine-dev"

/**
 * The GCP project hosting the container registry.
 *
 * Looked up under [gcpProjectKey] as a Gradle project property first, so that
 * `./gradlew jib -PGCP_PROJECT=<id>` selects the target project, then as a system
 * property, and finally as an environment variable.
 *
 * The order and the [default][defaultGcpProject] reproduce those of the `prepareExtras`
 * helper this replaced: pushing to the wrong registry is worse than failing to deploy,
 * so a deployment command must not silently fall back to the default.
 */
val gcpProject: String = providers.gradleProperty(gcpProjectKey)
    .orElse(providers.systemProperty(gcpProjectKey))
    .orElse(providers.environmentVariable(gcpProjectKey))
    .getOrElse(defaultGcpProject)

fun git(vararg args: String): String = providers.exec {
    commandLine("git", *args)
}.standardOutput.asText.get().trim()

val buildUi = tasks.getByPath(":admin-ui:qbuild")

jib {
    to {
        image = "gcr.io/$gcpProject/simple-message-delivery-server"
        tags = setOf(
            "latest",
            git("log", "-1", "--pretty=%H"),
            git("log", "-1", "--pretty=%h"),
            "v$version"
        )
    }
    container {
        mainClass = appClassName
        ports = listOf("8080", "8484")
        jvmFlags = listOf("-XX:MaxRAMPercentage=90")
    }
    extraDirectories {
        paths {
            path {
                setFrom(buildUi.outputs.files.asPath)
                into = "/resources/static"
            }
        }
    }
}
tasks.named("jib") { dependsOn(buildUi) }
tasks.named("jibDockerBuild") { dependsOn(buildUi) }
