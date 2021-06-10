/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

plugins {
    java
}

group = "io.spine.message-delivery"
version = "0.1.0"

allprojects {
    apply(from = "$rootDir/version.gradle.kts")
    group = "io.spine.message-delivery"
    version = extra["messageDeliveryVersion"]!!

    apply<IdeaPlugin>()
}

subprojects {
    apply<JavaConventionPlugin>()
    apply<DependencyManagementPlugin>()
    apply<SpinePlugin>()
}
