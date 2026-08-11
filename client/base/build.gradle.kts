/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.spine.dependency.lib.Grpc
import io.spine.dependency.test.Kotest

dependencies {
    // `ManagedChannel` appears in the public API of `AbstractDeliveryBootstrapper`.
    api(Grpc.core)
    // Command, event, and rejection types of the Delivery server appear in
    // the public API of the client contracts.
    api(project(":model"))
    testImplementation(Kotest.assertions)
}

apply {
    from(rootDir.toPath().resolve("test-artifacts.gradle"))
}
