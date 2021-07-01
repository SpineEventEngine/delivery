/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

// https://logging.apache.org/log4j/2.x/
object Log4j2 {
    const val version = "2.14.1"
    const val core = "org.apache.logging.log4j:log4j-core:${version}"
    const val api = "org.apache.logging.log4j:log4j-api:${version}"
    const val slf4jBridge = "org.apache.logging.log4j:log4j-slf4j18-impl:${version}"
}
