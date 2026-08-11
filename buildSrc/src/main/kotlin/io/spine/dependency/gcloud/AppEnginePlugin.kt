/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
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
