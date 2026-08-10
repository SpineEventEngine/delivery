/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.dependency.storage

/**
 * [Hazelcast](https://hazelcast.com/) — the in-memory data grid backing
 * the `storage:hazelcast` module.
 *
 * This file is owned by this repository — the `config` distribution
 * does not declare Hazelcast.
 */
@Suppress("unused", "ConstPropertyName")
object Hazelcast {
    // https://central.sonatype.com/artifact/com.hazelcast/hazelcast
    const val version = "5.7.0"
    const val lib = "com.hazelcast:hazelcast:$version"
}
