/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.spine.internal.dependency.Grpc
import io.spine.internal.dependency.Log4j2
import io.spine.internal.dependency.Spine

dependencies {
    implementation(Log4j2.api)
    implementation(project(":model"))
    implementation(Grpc.core)
    implementation(Spine.server)
    testImplementation(Spine.Test.server)
    testRuntimeOnly(Grpc.nettyShaded)
}
