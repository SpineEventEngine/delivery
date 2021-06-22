/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */
import io.spine.internal.dependency.Spine

plugins {
    idea
    `java-convention`
    `dependency-management`
    `code-quality`
    spine
}

apply(from = "$rootDir/../version.gradle.kts")
group = "io.spine.message-delivery"
version = extra["messageDeliveryVersion"]!!

dependencies {
    implementation(Spine.Stable.server)
    implementation(Spine.Stable.client)
    protobuf("io.spine.message-delivery:model:${version}")
}
