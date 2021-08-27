/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.spine.internal.dependency.CheckerFramework
import io.spine.internal.dependency.ErrorProne
import io.spine.internal.dependency.FindBugs
import io.spine.internal.dependency.Flogger
import io.spine.internal.dependency.Gson
import io.spine.internal.dependency.Guava
import io.spine.internal.dependency.JavaX
import io.spine.internal.dependency.Protobuf
import io.spine.internal.dependency.Spine
import io.spine.internal.dependency.Truth

plugins {
    `java-library`
}

repositories {
    mavenCentral()
    google()
    maven("https://spine.mycloudrepo.io/public/repositories/releases") {
        content {
            includeGroup("io.spine")
            includeGroup("io.spine.tools")
            includeGroup("io.spine.gcloud")
        }
        mavenContent {
            releasesOnly()
        }
    }
    maven("https://spine.mycloudrepo.io/public/repositories/snapshots")
    spine("base")
    spine("base-types")
    spine("core-java")
}

configurations.all {
    resolutionStrategy {
        force(
            CheckerFramework.annotations,
            ErrorProne.annotations,
            Guava.lib,
            Guava.testLib,
            FindBugs.annotations,
            Flogger.lib,
            Gson.lib,
            JavaX.annotations,
            Protobuf.libs,
            Truth.libs,
            Spine.base,
            Spine.core,
            Spine.server,
            Spine.client,
            Spine.Test.base,
            Spine.Test.core,
            Spine.Test.server,
            Spine.Test.client
        )
    }
}

/**
 * Adds and configures a Spine's GitHub Packages Maven repository.
 *
 * @see [RepositoryHandler.maven]
 * @see [MavenArtifactRepository.setUrl]
 * @see [MavenArtifactRepository.credentials]
 */
fun RepositoryHandler.spine(repoName: Any) =
    maven {
        setUrl("https://maven.pkg.github.com/SpineEventEngine/${repoName}")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
