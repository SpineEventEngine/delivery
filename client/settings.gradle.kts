/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

rootProject.name = "delivery-server-client"
include("base")
include("simple-client")
include("demo")
include("testutil-client")
include("integration-test")

deployment("demo-appengine-8")
deployment("demo-appengine-11")

fun deployment(name: String) {
    val path = ":${name}"
    include(path)
    project(path).projectDir = file("./deployment/${name}")
}
