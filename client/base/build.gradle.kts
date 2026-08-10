/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.spine.dependency.lib.Grpc

dependencies {
    // `ManagedChannel` appears in the public API of `AbstractDeliveryBootstrapper`.
    api(Grpc.core)
    // Command, event, and rejection types of the Delivery server appear in
    // the public API of the client contracts.
    api(project(":model"))
}

apply {
    from(rootDir.toPath().resolve("test-artifacts.gradle"))
}
