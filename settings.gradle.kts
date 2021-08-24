/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

rootProject.name = "message-delivery"
include("redis-record-storage")
include("model")
include("server")
include("simple-server")
include("testutil-server")
includeBuild("client")

fun deployment(name: String) {
    val path = ":${name}"
    include(path)
    project(path).projectDir = file("./deployment/${name}")
}

deployment("server-cloud-run")
deployment("simple-server-cloud-run")

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
        maven("https://maven.pkg.github.com/SpineEventEngine/base-types") {
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
        maven("https://maven.pkg.github.com/SpineEventEngine/base") {
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
        maven("https://maven.pkg.github.com/SpineEventEngine/core-java") {
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
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
        maven("https://maven.pkg.github.com/SpineEventEngine/base-types") {
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
        maven("https://maven.pkg.github.com/SpineEventEngine/base") {
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
        maven("https://maven.pkg.github.com/SpineEventEngine/core-java") {
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
