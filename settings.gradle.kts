/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

rootProject.name = "message-delivery"
include("model")
include("grpc-api")
include("server")
include("simple-server")
include("testutil-server")
include("admin-server")
include("admin-ui")
include("storage:hazelcast")
include("storage:redis")
include("storage:base")
includeBuild("client")

deployment("server-cloud-run")
deployment("simple-server-cloud-run")

fun deployment(name: String) {
    val path = ":${name}"
    include(path)
    project(path).projectDir = file("./deployment/${name}")
}
