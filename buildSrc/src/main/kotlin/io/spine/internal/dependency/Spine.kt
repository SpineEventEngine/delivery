/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

// https://github.com/SpineEventEngine
object Spine {
    const val spineReleaseVersion = "1.7.4"
    private const val baseVersion = "2.0.0-SNAPSHOT.35"
    private const val coreVersion = "2.0.0-SNAPSHOT.25"

    const val base = "io.spine:spine-base:${baseVersion}"
    const val client = "io.spine:spine-client:${coreVersion}"
    const val server = "io.spine:spine-server:${coreVersion}"
    const val core = "io.spine:spine-core:${coreVersion}"

    object Test {
        const val base = "io.spine.tools:spine-testlib:${baseVersion}"
        const val client = "io.spine.tools:spine-testutil-client:${coreVersion}"
        const val server = "io.spine.tools:spine-testutil-server:${coreVersion}"
        const val core = "io.spine.tools:spine-testutil-core:${coreVersion}"
    }

    object Stable {
        const val base = "io.spine:spine-base:${spineReleaseVersion}"
        const val client = "io.spine:spine-client:${spineReleaseVersion}"
        const val server = "io.spine:spine-server:${spineReleaseVersion}"
        const val core = "io.spine:spine-core:${spineReleaseVersion}"
    }
}
