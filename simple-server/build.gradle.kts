/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.spine.internal.dependency.Grpc
import io.spine.internal.dependency.Log4j2
import io.spine.internal.dependency.Spine

dependencies {
    implementation(Log4j2.api)
    implementation(project(":grpc-api"))
    implementation(project(":storage:redis"))
    implementation(project(":storage:hazelcast"))
    implementation(Grpc.core)
    implementation(Spine.server)
    testImplementation(project(":testutil-server"))
    testImplementation(project(path = ":grpc-api", configuration = "testArtifacts"))
    testRuntimeOnly(Grpc.nettyShaded)

    // Use this one to run the app locally.
//    implementation(Grpc.nettyShaded)
}
