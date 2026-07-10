/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.spine.dependency.lib.Caffeine
import io.spine.dependency.lib.Grpc
import io.spine.dependency.lib.GrpcKotlin
import io.spine.dependency.lib.Micronaut
import io.spine.dependency.lib.Slf4J
import io.spine.dependency.test.JUnit
import io.spine.dependency.test.Kotest

plugins {
    `micronaut-application`
}

application {
    mainClass.set("io.spine.delivery.admin.AdminServer")
}

// This build runs with `failOnVersionConflict()` (see `forceVersions()` applied by
// the root project), while the Micronaut module POMs request slightly older patch
// versions of each other than the ones the platform BOM pins. Align each group to
// the platform's pick — the same way the root build aligns the `io.grpc` group.
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

// The CoreJvm compiler applies KSP to every module, and the Micronaut plugin,
// seeing KSP, wires `micronaut-inject-kotlin` and its BOM into the KSP
// configurations. This module has no Kotlin sources, so the Micronaut KSP
// processor has nothing to do there — while its transitive versions clash with
// the Spine compiler stack under `failOnVersionConflict()`. Keep the Micronaut
// processing on the Java annotation-processing path only.
configurations.matching { it.name.startsWith("ksp") }.configureEach {
    withDependencies {
        removeAll { it.group?.startsWith(Micronaut.group) == true }
    }
}

micronaut {
    version.set(Micronaut.version)

    runtime("netty")

    processing {
        incremental(true)
        annotations("io.spine.delivery.admin.*")
    }
}

dependencies {
    // This build runs with `failOnVersionConflict()` (see `forceVersions()` applied
    // by the root project). The Micronaut modules come from nested BOMs that request
    // slightly different patch versions of each other, so the platform BOM is
    // enforced on every configuration to collapse those conflicts.
    annotationProcessor(enforcedPlatform(Micronaut.bom))
    annotationProcessor(Micronaut.AnnotationProcessor.httpValidation)
    annotationProcessor(Micronaut.AnnotationProcessor.security)

    implementation(enforcedPlatform(Micronaut.bom))
    implementation(Micronaut.runtime)
    implementation(Micronaut.jacksonDatabind)
    implementation(Micronaut.reactor)
    implementation(Micronaut.security)
    // The gRPC stubs and Protobuf runtime come via `:grpc-api` (`api` dependencies).
    // The Netty transport is the shaded one: it keeps gRPC's Netty inside the
    // `io.grpc.netty.shaded` namespace, away from the Netty managed by Micronaut.
    implementation(Grpc.nettyShaded)
    implementation(project(":grpc-api"))

    // Micronaut 4 no longer ships YAML support by default, and this application
    // is configured by `application.yml`.
    runtimeOnly(Micronaut.snakeYaml)

    testAnnotationProcessor(enforcedPlatform(Micronaut.bom))
    testImplementation(enforcedPlatform(Micronaut.bom))
    testImplementation(Micronaut.Test.core)
    testImplementation(Micronaut.Test.jUnit5)
    testImplementation(Micronaut.httpClient)
}
