/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

// https://errorprone.info/
@Suppress("unused")
object ErrorProne {
    private const val version = "2.9.0"
    private const val nullawayVersion = "0.9.2"

    val annotations = listOf(
        "com.google.errorprone:error_prone_annotations:${version}",
        "com.google.errorprone:error_prone_type_annotations:${version}"
    )
    const val core = "com.google.errorprone:error_prone_core:${version}"
    const val checkApi = "com.google.errorprone:error_prone_check_api:${version}"
    const val testHelpers = "com.google.errorprone:error_prone_test_helpers:${version}"

    object Plugin {

        // https://github.com/uber/NullAway
        const val nullaway = "com.uber.nullaway:nullaway:${nullawayVersion}"
    }
}
