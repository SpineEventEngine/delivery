import io.spine.internal.dependency.Protobuf

/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

plugins {
    idea
    `java-convention`
    `dependency-management`
    `code-quality`
    id("com.google.protobuf")
    id("io.spine.tools.gradle.bootstrap") version "1.7.1"
}

apply(from = "$rootDir/../version.gradle.kts")
group = "io.spine.message-delivery"
version = extra["messageDeliveryVersion"]!!

dependencies {
    Protobuf.libs.forEach { implementation(it) }
    protobuf("io.spine.message-delivery:server:${version}")
}
