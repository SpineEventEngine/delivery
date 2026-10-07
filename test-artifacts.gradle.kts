/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

import io.spine.gradle.SpineTaskGroup

// Shares the test classes of a module with the tests of other modules.
//
// A consumer declares, for example:
//
//     testImplementation(project(path = ":delivery-grpc-api", configuration = "testArtifacts"))
//
// and gets the `test`-classified JAR of the module along with the dependencies of its tests.
//
// This script is applied with `apply(from = ...)`, so the type-safe accessors that
// the `plugins {}` block generates, such as `sourceSets`, are not available here.
// The source sets are obtained at the top level, where the receiver is the project:
// inside a task configuration block, `the<T>()` would look up the task's extensions.
val sourceSets = the<SourceSetContainer>()

val testJar = tasks.register<Jar>("testJar") {
    group = SpineTaskGroup.name
    description = "Assembles a JAR with the compiled test classes and resources"
    archiveClassifier.set("test")
    from(sourceSets.named(SourceSet.TEST_SOURCE_SET_NAME).map { it.output })
}

// A consumable-only configuration: other projects select it by name, and nothing
// resolves it in this project.
//
// It extends only the configurations declaring the test dependencies, not
// the resolvable `testRuntimeClasspath`, which carries the same dependencies.
//
// It deliberately has no attributes. Gradle offers a consumable configuration to
// attribute-based variant selection only when it has attributes, so this one never
// competes with `apiElements` and `runtimeElements` for a plain project dependency.
configurations.consumable("testArtifacts") {
    extendsFrom(
        configurations.getByName(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME),
        configurations.getByName(JavaPlugin.TEST_RUNTIME_ONLY_CONFIGURATION_NAME)
    )
    outgoing.artifact(testJar)
}
