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

plugins {
    `java-test-fixtures`
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

    // The `given` fixtures and the `spine.test.delivery` Protobuf types they use.
    // `java-test-fixtures` puts them on the test classpath, so the `test` source set
    // needs no explicit dependency on them.
    testFixturesApi(project(":delivery-model"))
    testFixturesApi(CoreJvm.serverTestLib)

    testImplementation(project(path = ":grpc-api", configuration = "testArtifacts"))
    testImplementation(Kotest.assertions)
    testImplementation(Time.testLib)
    testRuntimeOnly(Grpc.nettyShaded)

    // Use this one to run the app locally.
//    implementation(Grpc.nettyShaded)
}
