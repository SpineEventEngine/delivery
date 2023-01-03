/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

// https://junit.org/junit5/
object JUnit {
    private const val version = "5.8.1"

    // https://github.com/apiguardian-team/apiguardian
    private const val apiGuardianVersion = "1.1.2"

    val api = listOf(
        "org.apiguardian:apiguardian-api:${apiGuardianVersion}",
        "org.junit.jupiter:junit-jupiter-api",
        "org.junit.jupiter:junit-jupiter-params"
    )
    val engine = "org.junit.jupiter:junit-jupiter-engine"
    const val bom = "org.junit:junit-bom:${version}"
}
