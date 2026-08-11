/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.spine.dependency.lib.Caffeine
import io.spine.dependency.lib.GrpcKotlin
import io.spine.dependency.lib.Micronaut
import io.spine.dependency.lib.Slf4J
import io.spine.dependency.test.JUnit
import io.spine.dependency.test.Kotest
import org.gradle.api.Project

/**
 * Aligns the versions of the Micronaut platform modules and their transitive
 * dependencies across all configurations of this project.
 *
 * This build runs with `failOnVersionConflict()` (see `forceVersions()` applied by
 * the root project), while the Micronaut module POMs request slightly older patch
 * versions of each other than the ones the platform BOM pins. Align each group to
 * the platform's pick — the same way the root build aligns the `io.grpc` group.
 *
 * Required by `admin-server`, which uses Micronaut directly, and by any module
 * resolving `admin-server` from its classpath — such as `delivery-server-cloud-run` —
 * because the conflicting constraints surface in every resolution of the
 * Micronaut graph.
 *
 * Update the aligned versions together with [Micronaut.version] — see the KDoc
 * of [Micronaut.coreVersion] and its siblings.
 */
fun Project.alignMicronautPlatform() {
    configurations.all {
        resolutionStrategy.eachDependency {
            when (requested.group) {
                Micronaut.group -> useVersion(Micronaut.coreVersion)
                "io.micronaut.reactor" -> useVersion(Micronaut.reactorVersion)
                "io.micronaut.serde" -> useVersion(Micronaut.serdeVersion)
                "io.micronaut.sourcegen" -> useVersion(Micronaut.sourcegenVersion)
                "org.apache.groovy" -> useVersion(Micronaut.groovyVersion)
                // Micronaut's processors request an older Caffeine than both its own
                // platform and this build; align to the version this build uses.
                "com.github.ben-manes.caffeine" ->
                    useVersion(Caffeine.lib.substringAfterLast(':'))
                // The Micronaut platform constrains SLF4J to an older patch version
                // than the one this build uses; align to the version used here.
                "org.slf4j" -> useVersion(Slf4J.lib.substringAfterLast(':'))
                // The root build aligns `io.grpc` to the gRPC BOM but deliberately
                // leaves the independently versioned `grpc-kotlin-*` artifacts alone.
                // The Micronaut platform constrains them to an older version than the
                // one Spine server is built against, so align them here.
                "io.grpc" -> if (requested.name.contains("kotlin")) {
                    useVersion(GrpcKotlin.version)
                }
                // `micronaut-test` is built against JUnit 5 and Kotest 5, while this
                // build forces JUnit 6 and Kotest 6. Align to the forced versions.
                "org.junit.jupiter", "org.junit.platform" -> useVersion(JUnit.version)
                Kotest.group -> useVersion(Kotest.version)
            }
        }
    }
}
