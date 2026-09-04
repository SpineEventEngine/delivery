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

package io.spine.dependency.gcloud

/**
 * The [App Engine Gradle plugin](https://github.com/GoogleCloudPlatform/app-gradle-plugin)
 * staging and deploying the `demo-appengine-11` module to Google App Engine.
 *
 * The version is declared here rather than taken from
 * [io.spine.dependency.lib.AppEngine.GradlePlugin]: that `config`-distributed object
 * pins `2.2.0`, which predates the Gradle 8/9 API cleanups this build runs on.
 *
 * This file is owned by this repository — the `config` distribution
 * does not use the plugin.
 */
@Suppress("unused", "ConstPropertyName")
object AppEnginePlugin {
    // https://plugins.gradle.org/plugin/com.google.cloud.tools.appengine-appyaml
    const val version = "2.8.5"

    /**
     * The ID of the `app.yaml`-based flavor of the plugin, serving the Java 11+
     * App Engine runtimes.
     */
    const val appYamlId = "com.google.cloud.tools.appengine-appyaml"
}
