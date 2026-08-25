/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.dependency.lib

/**
 * [Commons IO](https://commons.apache.org/proper/commons-io/) is
 * a transitive dependency that we don't use directly.
 */
object CommonsIo {
    private const val version = "2.15.1"
    const val lib = "commons-io:commons-io:$version"
}
