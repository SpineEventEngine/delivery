/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

// https://github.com/grpc/grpc-java
@Suppress("unused")
object Grpc {
    @Suppress("MemberVisibilityCanBePrivate")
    const val version = "1.39.0"
    const val core = "io.grpc:grpc-core:${version}"
    const val stub = "io.grpc:grpc-stub:${version}"
    const val okHttp = "io.grpc:grpc-okhttp:${version}"
    const val protobuf = "io.grpc:grpc-protobuf:${version}"
    const val netty = "io.grpc:grpc-netty:${version}"
    const val nettyShaded = "io.grpc:grpc-netty-shaded:${version}"
    const val context = "io.grpc:grpc-context:${version}"
    const val protobufPlugin = "io.grpc:protoc-gen-grpc-java:${version}"
}
