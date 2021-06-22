/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.spine.internal.dependency.Protobuf
import io.spine.internal.dependency.Spine

plugins {
    id("com.google.protobuf")
}

dependencies {
    Protobuf.libs.forEach { api(it) }
    protobuf(Spine.serverProto)
    runtimeOnly(Spine.server)
}

tasks.compileJava {
    // Remove linter checks to allow Protos compile without failing the build.
    options.compilerArgs.removeAll(listOf("-Xlint:unchecked", "-Xlint:deprecation", "-Werror"))
}
