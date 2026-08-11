/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
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

rootProject.name = "delivery-server"
include("model")
include("grpc-api")
include("simple-server")
include("testutil-server")
include("admin-server")
include("admin-ui")
include("storage:hazelcast")
include("storage:redis")
include("storage:base")

// The published client modules carry project names distinct from their directories,
// so that their Maven artifacts get the desired IDs:
//   `client/simple-client` -> `:client:delivery-client`      -> `spine-delivery-client`
//   `client/base`          -> `:client:delivery-client-base` -> `spine-delivery-client-base`
clientModule("delivery-client", inDirectory = "simple-client")
clientModule("delivery-client-base", inDirectory = "base")
include("client:testutil-client")
include("client:demo")
include("client:integration-test")

deployment("simple-server-cloud-run")
clientDeployment("demo-appengine-11")

fun deployment(name: String) {
    val path = ":${name}"
    include(path)
    project(path).projectDir = file("./deployment/${name}")
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
