/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

// https://github.com/google/guava
object Guava {
    private const val version = "30.1.1-jre"
    const val lib     = "com.google.guava:guava:${version}"
    const val testLib = "com.google.guava:guava-testlib:${version}"
}
