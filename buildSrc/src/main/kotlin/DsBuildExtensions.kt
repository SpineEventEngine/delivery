/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

@file:Suppress("unused", "TopLevelPropertyNaming", "ObjectPropertyName")

import io.spine.dependency.gcloud.AppEnginePlugin
import io.spine.dependency.lib.Jib
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
 * Shortcut for applying the [Micronaut application][Micronaut.GradlePlugin] Gradle plugin.
 */
val PluginDependenciesSpec.`micronaut-application`: PluginDependencySpec
    get() = id(Micronaut.GradlePlugin.id).version(Micronaut.GradlePlugin.version)

/**
 * Shortcut for applying the [Jib][Jib.GradlePlugin] Gradle plugin.
 */
val PluginDependenciesSpec.jib: PluginDependencySpec
    get() = id(Jib.GradlePlugin.id).version(Jib.GradlePlugin.version)

/**
 * Shortcut for applying the `app.yaml`-based [App Engine][AppEnginePlugin] Gradle plugin.
 */
val PluginDependenciesSpec.`appengine-appyaml`: PluginDependencySpec
    get() = id(AppEnginePlugin.appYamlId).version(AppEnginePlugin.version)
