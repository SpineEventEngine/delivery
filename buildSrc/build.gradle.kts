/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:2.0.1")
    implementation("com.google.protobuf:protobuf-gradle-plugin:0.8.16")
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}
