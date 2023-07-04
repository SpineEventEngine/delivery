/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.spine.internal.dependency.Grpc
import io.spine.internal.dependency.Spine

dependencies {
    Grpc.apply {
        api(stub)
        api(core)
        api(protobuf)
    }
    implementation(Spine.Stable.server)
    implementation(Spine.Stable.client)
    testImplementation(Spine.Stable.Test.server)
}

apply {
    from(rootDir.toPath().resolve("test-artifacts.gradle"))
}

// Path to the `/proto` directory for this module.
val localProtoPath = "./src/main/proto"

// We're explicitly copying protos to ensure rejections are generated.
// See https://github.com/SpineEventEngine/base/issues/650 for details.
val copyExternalProtos = tasks.create<Copy>("copyExternalProtos") {
    from("../../model/src/main/proto", "../../grpc-api/src/main/proto")
    into(localProtoPath)
}

// We're explicitly deleting copied protos to ensure the build is starting from scratch after
// each `clean` task.
//
// This is useful when external protos are changed (moved or files are renamed) but in this
// module we still have a copy of not updated files which makes the build pass, masking
// some possible import issues.
val deleteCopiedProto = tasks.create<Delete>("deleteCopiedProto") {
    delete(localProtoPath)
}

tasks.withType<com.google.protobuf.gradle.GenerateProtoTask> {
    dependsOn(copyExternalProtos)
}

tasks.clean {
    dependsOn(deleteCopiedProto)
}
