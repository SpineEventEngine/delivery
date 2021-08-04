/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */
package io.spine.internal.gradle

import org.codehaus.groovy.runtime.ProcessGroovyMethods
import org.gradle.api.Project

/**
 * The name of the variable used to retrieve the GCP project ID from.
 */
const val GCP_PROJECT = "GCP_PROJECT"

/**
 * Prepares extras for the supplied `project`.
 */
fun prepareExtras(project: Project): Extras {
    val gitExtras = GitExtras(
        hash = exec("git log -1 --pretty=%H"),
        shortHash = exec("git log -1 --pretty=%h")
    )
    return Extras(
        git = gitExtras,
        gcpProject = fetchGcpProject(project)
    )
}

/**
 * Retrieves GCP project ID.
 *
 * <p>First tries to use project properties, then checks system properties and then env variables.
 * Falls back to `spine-dev` if nothing else set.
 */
fun fetchGcpProject(project: Project): String {
    if (project.hasProperty(GCP_PROJECT)) {
        return project.property(GCP_PROJECT).toString()
    }
    val gcpProject = System.getProperty(GCP_PROJECT) ?: System.getenv(GCP_PROJECT)
    return gcpProject?.toString() ?: "spine-dev"
}

/**
 * Information retrieved from current Git state of the repository.
 */
data class GitExtras(
    /**
     * The full-length hash of the current Git commit.
     */
    val hash: String,
    /**
     * The short 7-chars-length hash of the current Git commit.
     */
    val shortHash: String
)

/**
 * Project and repository extra information.
 */
data class Extras(
    /**
     * Extras retrieved from Git.
     */
    val git: GitExtras,
    /**
     * The name the GCP project to be used for deployments.
     */
    val gcpProject: String
)

/**
 * Runs specified `command` and returns its text output.
 */
fun exec(command: String): String =
    ProcessGroovyMethods.getText(ProcessGroovyMethods.execute(command))
