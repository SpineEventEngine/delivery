/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.dependency.lib

/**
 * [Jib](https://github.com/GoogleContainerTools/jib) builds container images
 * for JVM applications; used by the `delivery-server-cloud-run` module.
 *
 * This file is owned by this repository — the `config` distribution does not
 * ship Jib, so the plugin must not be declared in `buildSrc/build.gradle.kts`,
 * which `./config/pull` overwrites.
 */
@Suppress("unused", "ConstPropertyName")
object Jib {

    /**
     * The Jib Gradle plugin, applied via the `jib` accessor declared
     * in `DsBuildExtensions.kt`.
     *
     * @see https://plugins.gradle.org/plugin/com.google.cloud.tools.jib
     */
    object GradlePlugin {
        const val id = "com.google.cloud.tools.jib"
        const val version = "3.4.4"
    }
}
