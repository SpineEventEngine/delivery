/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

rootProject.name = "delivery-server"
include("model")
include("grpc-api")
include("server")
include("simple-server")
include("testutil-server")
// TEMPORARILY PARKED during the CoreJvm/Gradle-9 migration — each needs its own Gradle-9
// plugin sub-migration before being re-enabled:
//   * `admin-server` — Micronaut Gradle plugin 3.x -> 4.x (and framework 3 -> 4).
//   * `admin-ui`     — Quasar/npm Gradle integration on Gradle 9.
// include("admin-server")
// include("admin-ui")
include("storage:hazelcast")
include("storage:redis")
include("storage:base")

// The `client` build stays on Spine 1.x / Gradle 6 (see `client/README.md`), which is
// incompatible with this build's Gradle 9 toolchain. It is therefore NOT composed here as
// `includeBuild("client")`; build it standalone via `client/gradlew`. It consumes the servers'
// published Protobuf artifacts rather than being wired in as a source dependency.

// TEMPORARILY PARKED — the Jib/Shadow packaging config needs Gradle-9 updates.
// deployment("server-cloud-run")
// deployment("simple-server-cloud-run")

fun deployment(name: String) {
    val path = ":${name}"
    include(path)
    project(path).projectDir = file("./deployment/${name}")
}
