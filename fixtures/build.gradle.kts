/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.spine.dependency.lib.Grpc
import io.spine.dependency.local.CoreJvm

/**
 * Test fixtures shared by the client and the server test suites.
 *
 * The module deliberately depends on neither, so that both can use it.
 */
dependencies {
    // `TestInboxMessages` builds `InboxMessage`s via `TestActorRequestFactory`.
    api(CoreJvm.serverTestLib)
    // `NoOpChannel` extends `ManagedChannel`.
    api(Grpc.core)
}
