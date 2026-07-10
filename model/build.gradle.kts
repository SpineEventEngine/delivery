/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.spine.dependency.lib.Grpc
import io.spine.dependency.local.CoreJvm

dependencies {
    api(Grpc.stub)
    api(Grpc.protobuf)
    implementation(CoreJvm.server)
}

apply {
    from(rootDir.toPath().resolve("test-artifacts.gradle"))
}
