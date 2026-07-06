/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

// https://www.testcontainers.org/
object Testcontainers {
    const val version = "2.0.5"

    const val lib = "org.testcontainers:testcontainers:${version}"
    // Testcontainers 2.x renamed its JUnit 5 module `junit-jupiter` -> `testcontainers-junit-jupiter`.
    const val junitJupiter = "org.testcontainers:testcontainers-junit-jupiter:${version}"
}
