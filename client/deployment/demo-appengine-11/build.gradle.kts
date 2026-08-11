/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.spine.dependency.lib.Log4j2
import io.spine.dependency.local.Logging
import io.spine.dependency.web.Jetty

plugins {
    application
    id("com.gradleup.shadow")
    `appengine-appyaml`
}

// The Spine Logging backend requests an older Log4j2 patch than the one
// this build uses. Under `failOnVersionConflict()` the disagreement is
// settled explicitly, taking the newer version.
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.apache.logging.log4j") {
            useVersion(Log4j2.version)
        }
    }
}

dependencies {
    Jetty.all.forEach { implementation(it) }
    implementation(project(":client:demo"))
    runtimeOnly(Log4j2.core)
    // Routes the SLF4J calls of Jetty to the Log4j2 backend above.
    runtimeOnly(Log4j2.slf4j2Bridge)
    runtimeOnly(Logging.log4j2Backend)
}

val appClassName = "io.spine.delivery.demo.JettyStarter"
val uberJarName = "app"
val uberJarDir = layout.buildDirectory.dir("uberJar")

application {
    mainClass.set(appClassName)
    applicationDefaultJvmArgs = listOf(
        "-Xdebug", "-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
    )
}

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
    destinationDirectory.set(uberJarDir)
}

/**
 * The name under which the target GCP project is passed to the build.
 *
 * Kept spelled the same way as in `deployment/simple-server-cloud-run`, so that
 * one `-PGCP_PROJECT=<id>` selects the target for both deployment modules.
 */
val gcpProjectKey = "GCP_PROJECT"

/**
 * The GCP project to deploy the demo application to, when no project is given.
 */
val defaultGcpProject = "spine-dev"

/**
 * The GCP project hosting the App Engine application.
 *
 * Looked up under [gcpProjectKey] as a Gradle project property first, so that
 * `./gradlew appengineDeploy -PGCP_PROJECT=<id>` selects the target project, then as
 * a system property, and finally as an environment variable.
 */
val gcpProject: String = providers.gradleProperty(gcpProjectKey)
    .orElse(providers.systemProperty(gcpProjectKey))
    .orElse(providers.environmentVariable(gcpProjectKey))
    .getOrElse(defaultGcpProject)

appengine {
    deploy {
        projectId = gcpProject
        version = "4"
    }
    stage {
        setArtifact(uberJarDir.get().file("${uberJarName}.jar").asFile)
    }
}

tasks.named("appengineStage") {
    dependsOn(tasks.named("shadowJar"))
}
