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
    id("application")
}

/** The GCP project ID used for deployment of the application. **/
//val gcpProject: String by project
val gcpProject = ""

dependencies {
    runtimeOnly(Grpc.nettyShaded)
    implementation(project(":model"))
    implementation(Spine.server)
    testImplementation(Spine.Test.server)
}

val shadowJar: ShadowJar by tasks
shadowJar.apply {
    mergeServiceFiles()
    mergeServiceFiles("desc.ref")
    manifest {
        attributes["Multi-Release"] = "true" // https://github.com/johnrengelman/shadow/issues/449
    }
}

application {
    mainClass.set("io.spine.message.delivery.server.App")
}

jib {
    to {
        image = "gcr.io/${gcpProject}/message-delivery-server"
        tags = setOf("latest")
    }
    container {
        mainClass = application.mainClass.get()
        ports = listOf("8080", "8484")
    }
}
