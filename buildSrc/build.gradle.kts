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
    spine("base")
    spine("base-types")
    spine("core-java")
}

val spineBaseVersion = "2.0.0-SNAPSHOT.47"

dependencies {
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:2.0.2")
    implementation("com.google.protobuf:protobuf-gradle-plugin:0.8.17")
    implementation("io.spine.tools:spine-mc-java:${spineBaseVersion}")
    implementation("gradle.plugin.com.github.jengelman.gradle.plugins:shadow:7.0.0")
    implementation("gradle.plugin.com.google.cloud.tools:jib-gradle-plugin:3.1.2")
}

/**
 * Adds and configures a Spine's GitHub Packages Maven repository.
 *
 * @see [RepositoryHandler.maven]
 * @see [MavenArtifactRepository.setUrl]
 * @see [MavenArtifactRepository.credentials]
 */
fun RepositoryHandler.spine(repoName: Any) =
    maven {
        setUrl("https://maven.pkg.github.com/SpineEventEngine/${repoName}")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
