/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

rootProject.name = "message-delivery-client"
include("client")
include("demo")
includeBuild("..") {
    dependencySubstitution {
        substitute(module("io.spine.message-delivery:model")).using(project(":model"))
    }
}

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
