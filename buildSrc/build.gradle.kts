/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

plugins {
    `kotlin-dsl`
}

repositories {
    mavenLocal()
    mavenCentral()
    google()
    gradlePluginPortal()
    maven("https://spine.mycloudrepo.io/public/repositories/releases") {
        content {
            includeGroup("io.spine")
            includeGroup("io.spine.tools")
            includeGroup("io.spine.gcloud")
        }
        mavenContent {
            releasesOnly()
        }
    }
    maven("https://spine.mycloudrepo.io/public/repositories/snapshots")
}

val spineBaseVersion = "2.0.0-SNAPSHOT.35"

dependencies {
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:2.0.1")
    implementation("com.google.protobuf:protobuf-gradle-plugin:0.8.16")
    implementation("io.spine.tools:spine-mc-java:${spineBaseVersion}")
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}
