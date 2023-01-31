/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

rootProject.name = "message-delivery"
include("redis-record-storage")
include("model")
include("server")
include("simple-server")
include("testutil-server")
includeBuild("client")

deployment("server-cloud-run")
deployment("simple-server-cloud-run")

fun deployment(name: String) {
    val path = ":${name}"
    include(path)
    project(path).projectDir = file("./deployment/${name}")
}
include("admin-server")
