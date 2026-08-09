/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.dependency.lib

/**
 * The SLF4J-to-Log4j2 bridge, routing SLF4J 2.x calls — such as those of
 * Micronaut in `admin-server` — to the Log4j2 backend used by the deployed image.
 *
 * This file is owned by this repository: the [Log4j2] object distributed by
 * the `config` module no longer declares the bridge artifact.
 *
 * The [version] must be kept in sync with `io.spine.dependency.lib.Log4j2`.
 */
// https://central.sonatype.com/artifact/org.apache.logging.log4j/log4j-slf4j2-impl
@Suppress("unused", "ConstPropertyName")
object Log4j2Bridge {
    private const val version = "2.26.0"
    const val slf4j2 = "org.apache.logging.log4j:log4j-slf4j2-impl:$version"
}
