/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

// https://github.com/google/flogger
object Flogger {
    internal const val version = "0.7.1"
    const val lib = "com.google.flogger:flogger:${version}"

    @Suppress("unused")
    object Runtime {
        const val systemBackend = "com.google.flogger:flogger-system-backend:${version}"
        const val log4J = "com.google.flogger:flogger-log4j:${version}"
        const val slf4J = "com.google.flogger:slf4j-backend-factory:${version}"
        const val log4J2 = "com.google.flogger:flogger-log4j2-backend:${version}"
    }
}
