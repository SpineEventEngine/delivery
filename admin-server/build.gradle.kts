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

import io.spine.dependency.lib.Grpc
import io.spine.dependency.lib.Micronaut

plugins {
    `micronaut-application`
}

application {
    mainClass.set("io.spine.delivery.admin.AdminServer")
}

// Align the Micronaut graph under `failOnVersionConflict()`.
// See the KDoc of `alignMicronautPlatform()` in `buildSrc`.
alignMicronautPlatform()

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
    // enforced for the processor, compile, and test configurations declared below.
    // Configurations that do not extend them (e.g. `compileProtoPath`) are covered
    // by the `eachDependency` alignment above.
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
