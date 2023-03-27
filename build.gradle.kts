/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */
import io.spine.internal.gradle.PublishingRepos
import io.spine.internal.gradle.spinePublishing

allprojects {
    apply(from = "$rootDir/version.gradle.kts")
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

    dependencies {
        add("implementation", io.spine.internal.dependency.Log4j2.slf4jBridge)
    }
}

spinePublishing {
    projectsToPublish.addAll(
        "server", "simple-server", "model"
    )
    targetRepositories.addAll(
        PublishingRepos.gitHub("message-delivery")
    )
    spinePrefix.set(false)
}
