/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */
import io.spine.internal.gradle.PublishingRepos
import io.spine.internal.gradle.spinePublishing

/**
 * Client build is still working on Gradle v6 while the latest Spine 1.x is not yet compatible
 * with Gradle v7.
 */

allprojects {
    apply(from = "$rootDir/../version.gradle.kts")
    group = "io.spine.message-delivery"
    version = extra["messageDeliveryVersion"]!!

    apply<IdeaPlugin>()
}

subprojects {
    apply<JavaConventionPlugin>()
    apply<JavadocConventionPlugin>()
    apply<DependencyManagementPlugin>()
    apply<CodeQualityPlugin>()
    apply<SpinePlugin>()

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
}

spinePublishing {
    projectsToPublish.addAll(
        "client", "simple-client", "base"
    )
    targetRepositories.addAll(
        PublishingRepos.gitHub("message-delivery")
    )
    spinePrefix.set(false)
}
