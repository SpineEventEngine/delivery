/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */
import io.spine.internal.dependency.Flogger
import io.spine.internal.dependency.Grpc
import io.spine.internal.dependency.Log4j2
import io.spine.internal.dependency.Spine

plugins {
    idea
    `java-convention`
    `dependency-management`
    `code-quality`
    spine
    war
    id("com.google.cloud.tools.appengine-appenginewebxml")
}

//TODO:2021-06-30:yuri-sergiichuk: add ability to configure the project from a property file or
// environment variable.
/** The GCP project ID used for deployment of the application. **/
val gcpProject = "spine-dev"

apply(from = "$rootDir/../version.gradle.kts")
group = "io.spine.message-delivery"
version = extra["messageDeliveryVersion"]!!

dependencies {
    runtimeOnly(Grpc.nettyShaded)
    runtimeOnly(Log4j2.slf4jBridge)
    runtimeOnly(Log4j2.core)
    runtimeOnly(Flogger.Runtime.log4J2)
    implementation(project(":client"))
    implementation(Log4j2.api)
    implementation(Spine.Stable.server)
    implementation(Spine.Stable.client)
    implementation("com.google.appengine:appengine-api-1.0-sdk:+")  // Latest App Engine APIs.
    providedCompile("javax.servlet:javax.servlet-api:3.1.0")
}

appengine {
    deploy {
        projectId = gcpProject
        version = "2"
    }
    run {
        jvmFlags = listOf(
            "-Xdebug",
            "-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
        )
    }
}
