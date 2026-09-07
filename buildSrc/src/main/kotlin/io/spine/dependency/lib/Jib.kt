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
        const val version = "3.5.4"
    }
}
