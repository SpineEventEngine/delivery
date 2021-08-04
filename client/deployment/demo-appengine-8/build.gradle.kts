/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */
plugins {
    war
    id("com.google.cloud.tools.appengine-appenginewebxml")
}

val extras by extra(io.spine.internal.gradle.prepareExtras(project))

dependencies {
    implementation(project(":demo"))
}

appengine {
    deploy {
        projectId = extras.gcpProject
        version = "3"
    }
    run {
        // Exposes a debug port for the local AppEngine server debug sessions.
        jvmFlags = listOf(
            "-Xdebug",
            "-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
        )
    }
}
