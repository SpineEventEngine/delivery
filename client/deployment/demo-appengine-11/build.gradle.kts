/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.spine.internal.dependency.Jetty

plugins {
    application
    id("com.github.johnrengelman.shadow")
    id("com.google.cloud.tools.appengine-appyaml")
}

val extras by extra(io.spine.internal.gradle.prepareExtras(project))

dependencies {
    Jetty.all.forEach { implementation(it) }
    implementation(project(":demo"))
}
val uberJarName = "app"
val uberJarFolder = "${buildDir}/uberJar"
val appClassName = "io.spine.message.delivery.demo.JettyStarter"
project.setProperty("mainClassName", appClassName)

tasks.withType<ShadowJar> {
    archiveBaseName.set(uberJarName)
    archiveClassifier.set("")
    archiveVersion.set("")

    mergeServiceFiles()
    mergeServiceFiles("desc.ref")
    manifest {
        attributes["Multi-Release"] = "true" // https://github.com/johnrengelman/shadow/issues/449
        attributes["Main-Class"] = appClassName
    }
    destinationDirectory.set(file(uberJarFolder))
}

application {
    applicationDefaultJvmArgs = listOf(
        "-Xdebug", "-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
    )
}

appengine {
    deploy {
        projectId = extras.gcpProject
        version = "4"
    }
    stage {
        setArtifact(file("${uberJarFolder}/${uberJarName}.jar"))
    }
}

tasks.getByName("appengineStage").dependsOn(tasks.getByName("shadowJar"))
