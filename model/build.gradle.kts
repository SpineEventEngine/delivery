/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.spine.internal.dependency.Grpc
import io.spine.internal.dependency.Spine

dependencies {
    Grpc.apply {
        api(stub)
        api(protobuf)
    }
    implementation(Spine.server)
}
