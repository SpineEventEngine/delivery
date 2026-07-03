/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.gradle

import java.io.File
import java.net.URI
import java.util.*
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler

/**
 * A Maven repository.
 */
data class Repository(
    val releases: String,
    val snapshots: String,
    private val credentialsFile: String? = null,
    private val credentialValues: ((Project) -> Credentials?)? = null,
    val name: String = "Maven repository `$releases`"
) {

    /**
     * Obtains the publishing password credentials to this repository.
     *
     * If the credentials are represented by a `.properties` file, reads the file and parses
     * the credentials. The file must have properties `user.name` and `user.password`, which store
     * the username and the password for the Maven repository auth.
     */
    fun credentials(project: Project): Credentials? {
        if (credentialValues != null) {
            return credentialValues.invoke(project)
        }
        credentialsFile!!
        val log = project.logger
        log.info("Using credentials from `$credentialsFile`.")
        val file = project.rootProject.file(credentialsFile)
        if (!file.exists()) {
            return null
        }
        val creds = file.readCredentials()
        log.info("Publishing build as `${creds.username}`.")
        return creds
    }

    private fun File.readCredentials(): Credentials {
        val properties = Properties()
        properties.load(inputStream())
        val username = properties.getProperty("user.name")
        val password = properties.getProperty("user.password")
        return Credentials(username, password)
    }

    override fun toString(): String {
        return name
    }
}

/**
 * Password credentials for a Maven repository.
 */
data class Credentials(
    val username: String?,
    val password: String?
)

/**
 * Repositories to which we may publish. Normally, only one repository will be used.
 *
 * See `publish.gradle` for details of the publishing process.
 */
object PublishingRepos {

    val cloudRepo = Repository(
        name = "CloudRepo",
        releases = "https://spine.mycloudrepo.io/public/repositories/releases",
        snapshots = "https://spine.mycloudrepo.io/public/repositories/snapshots",
        credentialsFile = "cloudrepo.properties"
    )

    fun gitHub(repoName: String): Repository {
        var githubActor: String? = System.getenv("GITHUB_ACTOR")
        githubActor = if (githubActor.isNullOrEmpty()) {
            "developers@spine.io"
        } else {
            githubActor
        }

        return Repository(
            name = "GitHub Packages",
            releases = "https://maven.pkg.github.com/SpineEventEngine/$repoName",
            snapshots = "https://maven.pkg.github.com/SpineEventEngine/$repoName",
            credentialValues = { project ->
                Credentials(
                    username = githubActor,
                    // This is a trick. Gradle only supports password or AWS credentials. Thus,
                    // we pass the GitHub token as a "password".
                    // https://docs.github.com/en/actions/guides/publishing-java-packages-with-gradle#publishing-packages-to-github-packages
                    password = readGitHubToken(project)
                )
            }
        )
    }

    @Suppress("UNUSED_PARAMETER") // `project` kept for call-site compatibility.
    private fun readGitHubToken(project: Project): String =
        // Resolution from GitHub Packages requires a token with the `read:packages` scope,
        // supplied via the `GITHUB_TOKEN` environment variable.
        System.getenv("GITHUB_TOKEN") ?: ""
}

/**
 * Defines names of additional repositories commonly used in the framework projects.
 *
 * @see [applyStandard]
 */
@Suppress("unused")
object Repos {

    val spine: String = PublishingRepos.cloudRepo.releases
    val spineSnapshots: String = PublishingRepos.cloudRepo.snapshots

    const val sonatypeReleases: String = "https://oss.sonatype.org/content/repositories/snapshots"
    const val sonatypeSnapshots: String = "https://oss.sonatype.org/content/repositories/snapshots"
}

/**
 * The function to be used in `buildscript` clauses when fully-qualified call must be made.
 */
@Suppress("unused")
fun doApplyStandard(repositories: RepositoryHandler) {
    repositories.applyStandard()
}

/**
 * Applies repositories commonly used by Spine Event Engine projects.
 */
@Suppress("unused")
fun RepositoryHandler.applyStandard() {

    apply {
        gradlePluginPortal()
        mavenLocal()

        val libraryGroup = "io.spine"
        val toolsGroup = "io.spine.tools"
        val gcloudGroup = "io.spine.gcloud"

        maven {
            url = URI(Repos.spine)
            content {
                includeGroup(libraryGroup)
                includeGroup(toolsGroup)
                includeGroup(gcloudGroup)
            }
        }
        maven {
            url = URI(Repos.spineSnapshots)
            content {
                includeGroup(libraryGroup)
                includeGroup(toolsGroup)
                includeGroup(gcloudGroup)
            }
        }
        mavenCentral()
        maven {
            url = URI(Repos.sonatypeReleases)
        }
        maven {
            url = URI(Repos.sonatypeSnapshots)
        }
    }
}
