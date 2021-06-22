/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

// https://github.com/SpineEventEngine
object Spine {
    private const val baseVersion = "2.0.0-SNAPSHOT.35"
    private const val coreVersion = "2.0.0-SNAPSHOT.26"

    const val base = "io.spine:spine-base:${baseVersion}"
    const val client = "io.spine:spine-client:${coreVersion}"
    const val server = "io.spine:spine-server:${coreVersion}"
    const val serverProto = "io.spine:spine-server:${coreVersion}:proto"
    const val core = "io.spine:spine-core:${coreVersion}"

    object Test {
        const val base = "io.spine.tools:spine-testlib:${baseVersion}"
        const val client = "io.spine.tools:spine-testutil-client:${coreVersion}"
        const val server = "io.spine.tools:spine-testutil-server:${coreVersion}"
        const val core = "io.spine.tools:spine-testutil-core:${coreVersion}"
    }

    object Stable {
        
        const val version = "1.7.4"
        const val timeVersion = "1.7.1"

        const val base = "io.spine:spine-base:${version}"
        const val client = "io.spine:spine-client:${version}"
        const val server = "io.spine:spine-server:${version}"
        const val core = "io.spine:spine-core:${version}"
        const val time = "io.spine:spine-time:${timeVersion}"

        object Test {
            const val base = "io.spine:spine-testlib:${version}"
            const val client = "io.spine:spine-testutil-client:${version}"
            const val server = "io.spine:spine-testutil-server:${version}"
            const val core = "io.spine:spine-testutil-core:${version}"
            const val time = "io.spine:spine-testutil-time:${timeVersion}"
        }
    }
}
