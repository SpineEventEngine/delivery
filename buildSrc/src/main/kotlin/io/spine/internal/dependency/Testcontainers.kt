/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

// https://www.testcontainers.org/
object Testcontainers {
    const val version = "1.16.0"

    const val lib = "org.testcontainers:testcontainers:${version}"
    const val junitJupiter = "org.testcontainers:junit-jupiter:${version}"
}
