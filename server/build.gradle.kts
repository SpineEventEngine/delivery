/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.spine.internal.dependency.Grpc
import io.spine.internal.dependency.Spine

plugins {
    id("com.google.cloud.tools.jib")
    id("com.github.johnrengelman.shadow")
}

/** The GCP project ID used for deployment of the application. **/
//val gcpProject: String by project
val gcpProject = "bko-dev-firestore"

dependencies {
    runtimeOnly(Grpc.nettyShaded)
    implementation(project(":model"))
    implementation(Spine.server)
    testImplementation(Spine.Test.server)
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
