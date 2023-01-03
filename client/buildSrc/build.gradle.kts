/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
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

val spineBaseVersion = "1.8.0"

dependencies {
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:2.0.2")
    implementation("com.google.protobuf:protobuf-gradle-plugin:0.8.17")
    implementation("io.spine.tools:spine-protoc-api:${spineBaseVersion}")
    implementation("io.spine.tools:spine-plugin-base:${spineBaseVersion}")
    implementation("io.spine.tools:spine-model-compiler:${spineBaseVersion}")
    implementation("com.github.jengelman.gradle.plugins:shadow:6.1.0")
    implementation("com.google.cloud.tools:appengine-gradle-plugin:2.4.1")
}
