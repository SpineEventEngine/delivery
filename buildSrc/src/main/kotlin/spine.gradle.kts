/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import com.google.protobuf.gradle.id
import io.spine.internal.dependency.Grpc
import io.spine.internal.dependency.Protobuf
import io.spine.internal.dependency.Spine

plugins {
    `java-library`
    idea
    id("com.google.protobuf")
    id("io.spine.core-jvm")
}

// The CoreJvm Compiler / ProtoData wires the generated Java sources into the source sets;
// the Protobuf plugin additionally runs the gRPC service-stub generator (`*Grpc` classes).
// No manual `sourceSets` / `idea` generated-dir configuration is required (unlike `mc-java`).
protobuf {
    plugins {
        id("grpc") {
            artifact = Grpc.protobufPlugin
        }
    }
    generateProtoTasks {
        all().configureEach {
            plugins {
                id("grpc")
            }
        }
    }
}

dependencies {
    Protobuf.libs.forEach { implementation(it) }
    implementation(Spine.base)
    implementation(Grpc.stub)
    implementation(Grpc.protobuf)
    testImplementation(Spine.Test.base)
}
