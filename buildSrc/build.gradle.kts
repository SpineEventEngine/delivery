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
    maven("https://maven.pkg.github.com/SpineEventEngine/*") {
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}

// The version of the CoreJvm Compiler (formerly `mc-java`), which brings in the
// `io.spine.core-jvm` Gradle plugin and, transitively, the Spine Compiler (`io.spine.compiler`).
val coreJvmCompilerVersion = "2.0.0-SNAPSHOT.080"

// The Kotlin Gradle plugin must be on the `buildSrc` classpath because the CoreJvm
// Compiler plugin applies Kotlin compiler-plugin support during code generation.
// Keep in sync with the Kotlin version bundled by the Gradle `kotlin-dsl`.
val kotlinVersion = "2.3.21"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

configurations.all {
    resolutionStrategy {
        // Avoid clashing with the Kotlin bundled by Gradle's `kotlin-dsl`.
        force(
            "org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion",
            "org.jetbrains.kotlin:kotlin-stdlib-common:$kotlinVersion",
            "org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion"
        )
    }
}

dependencies {
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:5.1.0")
    implementation("com.google.protobuf:protobuf-gradle-plugin:0.10.0")
    implementation("io.spine.tools:core-jvm-plugins:${coreJvmCompilerVersion}")
    implementation("com.gradleup.shadow:shadow-gradle-plugin:9.4.1")
    implementation("com.google.cloud.tools:jib-gradle-plugin:3.4.4")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
}
