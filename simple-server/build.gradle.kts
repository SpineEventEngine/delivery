/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.spine.dependency.lib.Grpc
import io.spine.dependency.lib.Log4j2
import io.spine.dependency.local.CoreJvm
import io.spine.dependency.local.Time
import io.spine.dependency.test.Kotest

dependencies {
    implementation(Log4j2.core)
    implementation(project(":model"))
    implementation(project(":grpc-api"))
    implementation(project(":storage:redis"))
    implementation(project(":storage:hazelcast"))
    implementation(Grpc.core)
    implementation(Grpc.inProcess)
    implementation(CoreJvm.server)
    testImplementation(project(":testutil-server"))
    testImplementation(project(path = ":grpc-api", configuration = "testArtifacts"))
    testImplementation(Kotest.assertions)
    testImplementation(Time.testLib)
    testRuntimeOnly(Grpc.nettyShaded)

    // Use this one to run the app locally.
//    implementation(Grpc.nettyShaded)
}
