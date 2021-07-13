/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.spine.internal.dependency.Flogger
import io.spine.internal.dependency.Grpc
import io.spine.internal.dependency.Log4j2
import io.spine.internal.dependency.Spine
import io.spine.internal.dependency.Testcontainers

plugins {
    spine
}

dependencies {
    Grpc.apply {
        api(stub)
        api(protobuf)
    }
    implementation(Spine.Stable.server)
    implementation(Spine.Stable.client)
    testImplementation(Spine.Stable.Test.server)
    testImplementation(Testcontainers.lib)
    testImplementation(Testcontainers.junitJupiter)
    testRuntimeOnly(Grpc.nettyShaded)
    testRuntimeOnly(Log4j2.slf4jBridge)
    testRuntimeOnly(Log4j2.core)
    testRuntimeOnly(Flogger.Runtime.log4J2)
}

// We're explicitly copying protos to ensure rejections are generated.
// See https://github.com/SpineEventEngine/base/issues/650 for details.
val copyExternalProtos = tasks.create<Copy>("copyExternalProtos") {
    from("../../model/src/main/proto")
    into("./src/main/proto")
}

tasks.withType<com.google.protobuf.gradle.GenerateProtoTask> {
    dependsOn(copyExternalProtos)
}
