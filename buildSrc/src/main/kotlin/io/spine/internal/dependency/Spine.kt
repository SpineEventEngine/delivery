/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

// https://github.com/SpineEventEngine
object Spine {
    // Latest published Spine v2.x snapshots.
    private const val baseVersion = "2.0.0-SNAPSHOT.423"
    private const val coreVersion = "2.0.0-SNAPSHOT.383"
    private const val testLibVersion = "2.0.0-SNAPSHOT.213"

    const val base = "io.spine:spine-base:${baseVersion}"
    const val client = "io.spine:spine-client:${coreVersion}"
    const val server = "io.spine:spine-server:${coreVersion}"
    const val serverProto = "io.spine:spine-server:${coreVersion}:proto"
    const val core = "io.spine:spine-core:${coreVersion}"

    object Test {
        const val base = "io.spine.tools:base-testlib:${testLibVersion}"
        const val client = "io.spine.tools:client-testlib:${coreVersion}"
        const val server = "io.spine.tools:server-testlib:${coreVersion}"
        const val core = "io.spine.tools:core-testlib:${coreVersion}"
    }

    object Stable {

        const val version = "1.9.0"
        const val coreVersion = "1.9.0"
        const val timeVersion = "1.9.0"

        const val base = "io.spine:spine-base:${version}"
        const val client = "io.spine:spine-client:${coreVersion}"
        const val server = "io.spine:spine-server:${coreVersion}"
        const val core = "io.spine:spine-core:${coreVersion}"
        const val time = "io.spine:spine-time:${timeVersion}"

        object Test {
            const val base = "io.spine:spine-testlib:${version}"
            const val client = "io.spine:spine-testutil-client:${coreVersion}"
            const val server = "io.spine:spine-testutil-server:${coreVersion}"
            const val core = "io.spine:spine-testutil-core:${coreVersion}"
            const val time = "io.spine:spine-testutil-time:${timeVersion}"
        }
    }
}
