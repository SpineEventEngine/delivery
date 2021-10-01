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
import io.spine.internal.dependency.Log4j2
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
    maven("https://maven.pkg.github.com/SpineEventEngine/*") {
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
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
            Flogger.Runtime.log4J,
            Flogger.Runtime.log4J2,
            Flogger.Runtime.slf4J,
            Flogger.Runtime.systemBackend,
            Log4j2.api,
            Log4j2.core,
            Log4j2.slf4jBridge,
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
