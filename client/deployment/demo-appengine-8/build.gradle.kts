/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */
plugins {
    war
    id("com.google.cloud.tools.appengine-appenginewebxml")
}

//TODO:2021-06-30:yuri-sergiichuk: add ability to configure the project from a property file or
// environment variable.
/** The GCP project ID used for deployment of the application. **/
val gcpProject = "spine-dev"

dependencies {
    implementation(project(":demo"))
}

appengine {
    deploy {
        projectId = gcpProject
        version = "2"
    }
    run {
        // Exposes a debug port for the local AppEngine server debug sessions.
        jvmFlags = listOf(
            "-Xdebug",
            "-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
        )
    }
}
