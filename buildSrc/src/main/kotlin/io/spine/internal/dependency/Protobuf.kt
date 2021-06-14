/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

// https://github.com/protocolbuffers/protobuf
@Suppress("MemberVisibilityCanBePrivate") // used directly from outside
object Protobuf {
    const val version = "3.17.3"
    val libs = listOf(
        "com.google.protobuf:protobuf-java:${version}",
        "com.google.protobuf:protobuf-java-util:${version}"
    )
    const val compiler = "com.google.protobuf:protoc:${version}"
}
