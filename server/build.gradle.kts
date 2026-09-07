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
import io.spine.dependency.lib.Log4j2
import io.spine.dependency.local.CoreJvm
import io.spine.dependency.local.Time
import io.spine.dependency.test.Kotest

plugins {
    module
}

dependencies {
    implementation(Log4j2.core)
    implementation(project(":delivery-model"))
    implementation(project(":grpc-api"))
    implementation(project(":storage:redis"))
    implementation(project(":storage:hazelcast"))
    implementation(Grpc.core)
    implementation(Grpc.inProcess)
    implementation(CoreJvm.server)
    testImplementation(project(":fixtures"))
    testImplementation(project(path = ":grpc-api", configuration = "testArtifacts"))
    testImplementation(Kotest.assertions)
    testImplementation(Time.testLib)
    testRuntimeOnly(Grpc.nettyShaded)

    // Use this one to run the app locally.
//    implementation(Grpc.nettyShaded)
}
