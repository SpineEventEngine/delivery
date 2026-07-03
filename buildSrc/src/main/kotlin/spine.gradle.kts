/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.spine.internal.dependency.Protobuf
import io.spine.internal.dependency.Spine

plugins {
    `java-library`
    idea
    id("com.google.protobuf")
    id("io.spine.core-jvm")
}

// Code generation (Protobuf + the CoreJvm Compiler / ProtoData) wires the generated
// source directories into the source sets automatically; no manual `sourceSets` or
// `idea` generated-dir configuration is required (unlike the former `mc-java` setup).

dependencies {
    Protobuf.libs.forEach { implementation(it) }
    implementation(Spine.base)
    testImplementation(Spine.Test.base)
}
