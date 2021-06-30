/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.spine.internal.dependency.Flogger
import io.spine.internal.dependency.Grpc
import io.spine.internal.dependency.Log4j2

plugins {
    id("com.google.cloud.tools.jib")
    id("com.github.johnrengelman.shadow")
}
//TODO:2021-06-30:yuri-sergiichuk: add ability to configure the project from a property file or
// environment variable.
/** The GCP project ID used for deployment of the application. **/
val gcpProject = "bko-dev-firestore"

dependencies {
    runtimeOnly(Grpc.nettyShaded)
    runtimeOnly(Log4j2.slf4jBridge)
    runtimeOnly(Log4j2.core)
    runtimeOnly(Flogger.Runtime.log4J2)
    implementation(project(":server"))
}

val appClassName = "io.spine.message.delivery.server.App"

tasks.withType<ShadowJar> {
    mergeServiceFiles()
    mergeServiceFiles("desc.ref")
    manifest {
        attributes["Multi-Release"] = "true" // https://github.com/johnrengelman/shadow/issues/449
        attributes["Main-Class"] = appClassName
    }
}

jib {
    to {
        image = "gcr.io/${gcpProject}/message-delivery-server"
        tags = setOf("latest")
    }
    container {
        mainClass = appClassName
        ports = listOf("8080", "8484")
    }
}
