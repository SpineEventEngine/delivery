/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.spine.internal.dependency.Spine
import io.spine.internal.dependency.Testcontainers

plugins {
    spine
}

dependencies {
    implementation(Spine.Stable.server)
    implementation(Spine.Stable.client)
    testImplementation(Testcontainers.lib)
    testImplementation(Testcontainers.junitJupiter)
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
