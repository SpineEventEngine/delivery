/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.spine.internal.dependency.Grpc
import io.spine.internal.dependency.Spine

dependencies {
    runtimeOnly(Grpc.nettyShaded)

    implementation(Spine.server)

    testImplementation(Spine.Test.server)
}
