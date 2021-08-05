/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

rootProject.name = "message-delivery-client"
include("base")
include("client")
include("simple-client")
include("demo")
include("testutil-client")

fun deployment(name: String) {
    val path = ":${name}"
    include(path)
    project(path).projectDir = file("./deployment/${name}")
}

deployment("demo-appengine-8")
deployment("demo-appengine-11")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
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
}

pluginManagement {
    repositories {
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
}
