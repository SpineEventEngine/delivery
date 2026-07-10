/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

@file:Suppress("UnusedReceiverParameter", "unused", "TopLevelPropertyNaming", "ObjectPropertyName")

import io.spine.dependency.lib.Micronaut
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec

/**
 * Extensions for Gradle build scripts specific to the Delivery Server (`Ds`) project,
 * complementing those declared in `BuildExtensions.kt`.
 *
 * `BuildExtensions.kt` is distributed by the `config` module and is overwritten by
 * `./config/pull`, so the accessors needed only by this repository live here,
 * in a file the repository owns.
 *
 * See the documentation in `BuildExtensions.kt` for why dependency objects cannot
 * be referenced under the `plugins` block directly.
 */
private const val ABOUT = ""

/**
 * Shortcut for applying the [Micronaut application][Micronaut.GradlePlugin]
 * Gradle plugin.
 */
val PluginDependenciesSpec.`micronaut-application`: PluginDependencySpec
    get() = id(Micronaut.GradlePlugin.id).version(Micronaut.GradlePlugin.version)
