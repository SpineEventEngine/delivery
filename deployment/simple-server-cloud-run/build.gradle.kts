/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.spine.internal.dependency.Flogger
import io.spine.internal.dependency.Grpc
import io.spine.internal.dependency.Log4j2

plugins {
    application
    id("com.google.cloud.tools.jib")
    id("com.github.johnrengelman.shadow")
}

val extras by extra(io.spine.internal.gradle.prepareExtras(project))

dependencies {
    runtimeOnly(Grpc.nettyShaded)
    runtimeOnly(Log4j2.slf4jBridge)
    runtimeOnly(Log4j2.core)
    runtimeOnly(Flogger.Runtime.log4J2)
    implementation(project(":simple-server"))
    implementation(project(":admin-server"))
    implementation(project(":admin-ui"))
}

application {
    applicationDefaultJvmArgs = listOf(
        "-Xdebug", "-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=7007"
    )
}

val appClassName = "io.spine.delivery.launcher.Launcher"
project.setProperty("mainClassName", appClassName)

tasks.withType<ShadowJar> {
    mergeServiceFiles()
    mergeServiceFiles("desc.ref")
    manifest {
        attributes["Multi-Release"] = "true" // https://github.com/johnrengelman/shadow/issues/449
        attributes["Main-Class"] = appClassName
    }
}

var buildUi = tasks.getByPath(":admin-ui:qbuild")

jib {
    to {
        image = "gcr.io/${extras.gcpProject}/simple-message-delivery-server"
        tags = setOf("latest", extras.git.hash, extras.git.shortHash, "v${version}")
    }
    container {
        mainClass = appClassName
        ports = listOf("8080", "8484")
        jvmFlags = listOf("-XX:MaxRAMPercentage=90")
    }
    extraDirectories {
        paths {
            path {
                setFrom(buildUi.outputs.files.asPath)
                into = "/resources/static"
            }
        }
    }
}
tasks.getByName("jib").dependsOn.add(buildUi)
tasks.getByName("jibDockerBuild").dependsOn.add(buildUi)
