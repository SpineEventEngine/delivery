/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */
import io.spine.internal.gradle.PublishingRepos
import io.spine.internal.gradle.spinePublishing

allprojects {
    apply(from = "$rootDir/../version.gradle.kts")
    group = "io.spine.message-delivery"
    version = extra["messageDeliveryVersion"]!!

    apply<IdeaPlugin>()
}

subprojects {
    apply<JavaConventionPlugin>()
    apply<DependencyManagementPlugin>()
    apply<CodeQualityPlugin>()
    apply<SpinePlugin>()
}

spinePublishing {
    projectsToPublish.addAll(
        "client"
    )
    targetRepositories.addAll(
        PublishingRepos.gitHub("message-delivery")
    )
    spinePrefix.set(false)
}
