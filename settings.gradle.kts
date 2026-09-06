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

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            // The App Engine Gradle plugin publishes no plugin markers,
            // so its IDs are mapped to the plugin artifact on Maven Central.
            if (requested.id.id.startsWith("com.google.cloud.tools.appengine")) {
                useModule("com.google.cloud.tools:appengine-gradle-plugin:${requested.version}")
            }
        }
    }
}

// Deliberately NOT `delivery-server`: that is the name of the `:delivery-server`
// subproject below, and two projects sharing a `group:name` collide on Gradle
// module identity. The root applies Kover and also aggregates every subproject
// into its `kover` configuration, so the collision made conflict resolution drop
// the subproject and silently discard the server module's coverage.
rootProject.name = "delivery"

// The published `model` module carries a project name distinct from its directory,
// so that its Maven artifact gets the desired ID:
//   `model` -> `:delivery-model` -> `spine-delivery-model`
// The directory is pinned explicitly because Gradle otherwise derives it from
// the project name, which no longer matches.
include("model")
with(project(":model")) {
    name = "delivery-model"
    projectDir = file("./model")
}

include("grpc-api")
include("fixtures")
// The published `server` module likewise carries a distinct project name:
//   `server` -> `:delivery-server` -> `spine-delivery-server`
include("server")
with(project(":server")) {
    name = "delivery-server"
    projectDir = file("./server")
}

include("admin-server")
include("admin-ui")
include("storage:hazelcast")
include("storage:redis")
include("storage:base")

// The published client modules carry project names distinct from their directories,
// so that their Maven artifacts get the desired IDs:
//   `client/client`       -> `:client:delivery-client`      -> `spine-delivery-client`
//   `client/base`          -> `:client:delivery-client-base` -> `spine-delivery-client-base`
clientModule("delivery-client", inDirectory = "client")
clientModule("delivery-client-base", inDirectory = "base")
include("client:demo")
include("client:integration-test")

// The Cloud Run deployment also carries a project name distinct from its
// directory, so that its artifact is named `spine-delivery-server-cloud-run`:
//   `deployment/cloud-run` -> `:delivery-server-cloud-run`
deployment("delivery-server-cloud-run", inDirectory = "cloud-run")
clientDeployment("demo-appengine-11")

fun deployment(name: String, inDirectory: String = name) {
    val path = ":${inDirectory}"
    include(path)
    with(project(path)) {
        this.name = name
        projectDir = file("./deployment/${inDirectory}")
    }
}

fun clientDeployment(name: String) {
    val path = ":${name}"
    include(path)
    project(path).projectDir = file("./client/deployment/${name}")
}

fun clientModule(name: String, inDirectory: String) {
    val path = ":client:${inDirectory}"
    include(path)
    with(project(path)) {
        this.name = name
        projectDir = file("./client/${inDirectory}")
    }
}
