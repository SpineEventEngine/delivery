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

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.spine.dependency.lib.Grpc
import io.spine.dependency.lib.Log4j2
import io.spine.dependency.lib.Micronaut
import io.spine.dependency.local.Logging
import io.spine.dependency.storage.Hazelcast
import io.spine.dependency.storage.Redisson

plugins {
    module
    application
    jib
    id("com.gradleup.shadow")
}

// This module resolves the Micronaut graph via `:admin-server`, so it needs the
// same version alignment under `failOnVersionConflict()`.
// See the KDoc of `alignMicronautPlatform()` in `buildSrc`.
alignMicronautPlatform()

// The fat-JAR classpath is the first place where the graph of `server`
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
    implementation(project(":delivery-server"))
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

fun git(vararg args: String): String = providers.exec {
    commandLine("git", *args)
}.standardOutput.asText.get().trim()

val buildUi = tasks.getByPath(":admin-ui:qbuild")

/**
 * The CPU architecture of this machine, in the terms Jib and Docker use.
 *
 * The image is built for the host by default, so that `jibDockerBuild` gives the tests
 * a native image: an `amd64` image on an Apple silicon Mac runs under emulation, much slower.
 * The `Publish containers` workflow overrides this with
 * `-Djib.from.platforms=linux/amd64,linux/arm64` to push a multi-architecture manifest.
 */
val hostArchitecture: String =
    if (System.getProperty("os.arch") in setOf("aarch64", "arm64")) "arm64" else "amd64"

jib {
    from {
        platforms {
            platform {
                architecture = hostArchitecture
                os = "linux"
            }
        }
    }
    to {
        // Declared in `buildSrc` so that the test gates and the `jib` push agree on
        // the name. `jib` authenticates to `*.pkg.dev` via Application Default
        // Credentials, the same way `CloudArtifactRegistry` does for Maven.
        image = DELIVERY_SERVER_IMAGE_NAME
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
    // `module.gradle.kts` feeds this file to the image-dependent test tasks as an input, so
    // the path is pinned here rather than left to Jib's default.
    outputPaths {
        imageId = layout.buildDirectory.file(DELIVERY_IMAGE_ID_FILE).get().asFile.path
    }
}
// `extraDirectories` takes the UI build's output as a plain path, which carries no task
// dependency, so every Jib task must depend on that build explicitly. A clean checkout has
// no `admin-ui/dist/spa` until it runs.
listOf("jib", "jibDockerBuild", "jibBuildTar").forEach { jibTask ->
    tasks.named(jibTask) { dependsOn(buildUi) }
}

// The image-dependent test tasks depend on `jibDockerBuild` (see `module.gradle.kts`),
// which needs a Docker daemon able to load a Linux image. A runner that declares itself
// unable to launch Linux containers skips the build; its tests skip themselves for the
// same reason.
tasks.named("jibDockerBuild") {
    onlyIf("`$WINDOWS_CI_NO_DOCKER` is not set") { !windowsCiWithoutDocker() }
}
