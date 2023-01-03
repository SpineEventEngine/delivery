/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

/**
 * Gson is a transitive dependency which we don't use directly.
 * We `force` it in [DependencyResolution.forceConfiguration()].
 *
 * [Gson](https://github.com/google/gson)
 */
object Gson {
    private const val version = "2.8.8"
    const val lib = "com.google.code.gson:gson:${version}"
}
