/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

allprojects {
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
    }
}
