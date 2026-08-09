/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.dependency.storage

/**
 * [Redisson](https://redisson.org/) — the Redis Java client backing
 * the `storage:redis` module.
 *
 * This file is owned by this repository — the `config` distribution
 * does not declare Redisson.
 */
@Suppress("unused", "ConstPropertyName")
object Redisson {
    // https://central.sonatype.com/artifact/org.redisson/redisson
    const val version = "4.7.0"
    const val lib = "org.redisson:redisson:$version"

    /**
     * Versions of the third-party libraries which this graph brings in, and which win
     * the cross-stack conflicts on the `simple-server-cloud-run` classpath, where the
     * Redisson graph meets the Micronaut one. Update together with [version].
     */
    const val byteBuddyVersion = "1.18.2"
    const val snakeYamlVersion = "2.6"
}
