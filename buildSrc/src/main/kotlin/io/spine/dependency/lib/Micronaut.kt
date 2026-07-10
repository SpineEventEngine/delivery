/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.dependency.lib

/**
 * The Micronaut framework, as used by the `admin-server` module.
 *
 * The [version] is the Micronaut Platform version. The [Gradle plugin][GradlePlugin]
 * turns it into the `micronaut-platform` BOM
 * (`micronaut { version.set(Micronaut.version) }`), so the module artifacts below
 * are declared without versions.
 */
// https://micronaut.io/
@Suppress("unused", "ConstPropertyName")
object Micronaut {

    /**
     * The Micronaut Gradle plugin, applied via the `micronaut-application`
     * accessor declared in `DsBuildExtensions.kt`.
     *
     * The plugin versions independently from the framework: 4.6.2 is the newest
     * version that runs on a Java 17 daemon — the 5.x line (the first to
     * officially support Gradle 9) is compiled for JVM 25. In practice, 4.6.2
     * configures and runs cleanly on Gradle 9.6.1.
     */
    // https://plugins.gradle.org/plugin/io.micronaut.application
    object GradlePlugin {
        const val id = "io.micronaut.application"
        const val version = "4.6.2"
    }

    // https://repo1.maven.org/maven2/io/micronaut/platform/micronaut-platform/
    const val version = "4.10.17"
    const val bom = "io.micronaut.platform:micronaut-platform:$version"

    /**
     * The versions pinned by the platform [bom], repeated here because this build
     * runs with `failOnVersionConflict()`.
     *
     * The Micronaut module POMs request slightly older patch versions of each other
     * than the ones the platform pins, which registers as a version conflict. The
     * `admin-server` build script aligns each group to the platform's pick with
     * a `resolutionStrategy.eachDependency` rule. Update together with [version].
     */
    const val coreVersion = "4.10.26"
    const val reactorVersion = "3.9.1"
    const val serdeVersion = "2.16.2"
    const val sourcegenVersion = "1.8.5"
    const val groovyVersion = "4.0.28"

    const val runtime = "io.micronaut:micronaut-runtime"

    /**
     * The `JsonMapper` implementation.
     *
     * Micronaut 3 shipped Jackson with the runtime; since Micronaut 4
     * an implementation must be chosen explicitly.
     */
    const val jacksonDatabind = "io.micronaut:micronaut-jackson-databind"

    const val reactor = "io.micronaut.reactor:micronaut-reactor"
    const val security = "io.micronaut.security:micronaut-security"
    const val httpClient = "io.micronaut.reactor:micronaut-reactor-http-client"

    object AnnotationProcessor {
        const val httpValidation = "io.micronaut:micronaut-http-validation"
        const val security = "io.micronaut.security:micronaut-security-annotations"
    }

    object Test {
        const val core = "io.micronaut.test:micronaut-test-core"
        const val jUnit5 = "io.micronaut.test:micronaut-test-junit5"
    }
}
