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

val spineBaseVersion = "2.0.0-SNAPSHOT.34"

dependencies {
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:2.0.1")
    implementation("com.google.protobuf:protobuf-gradle-plugin:0.8.16")
    //TODO:2021-06-10:yuri-sergiichuk: remove the `plugin-base` dependency as soon as
    // https://github.com/SpineEventEngine/base/pull/649 is merged.
    implementation("io.spine.tools:spine-plugin-base:${spineBaseVersion}")
    implementation("io.spine.tools:spine-mc-java:${spineBaseVersion}")
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}
