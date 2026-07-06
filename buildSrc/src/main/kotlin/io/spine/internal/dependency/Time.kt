/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

// https://github.com/SpineEventEngine/time
@Suppress("unused")
object Time {
    private const val version = "2.0.0-SNAPSHOT.242"
    const val lib = "io.spine:spine-time:${version}"
    const val testLib = "io.spine.tools:time-testlib:${version}"
}
