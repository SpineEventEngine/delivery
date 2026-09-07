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
import io.spine.dependency.test.Testcontainers

plugins {
    module
}

dependencies {
    implementation(project(":client:delivery-client"))
    testImplementation(project(":fixtures"))
    testImplementation(project(path = ":client:delivery-client-base",
                               configuration = "testArtifacts"))
    testImplementation(Testcontainers.lib)
    testImplementation(Testcontainers.junitJupiter)
    testRuntimeOnly(Grpc.nettyShaded)
}

// The `integration`-tagged suites run the Delivery server from a Docker image. They are
// no longer excluded: `checkDockerAvailable` enforces Docker, and each suite is annotated
// `@RequiresDeliveryImage`, which skips it when the image is absent.
