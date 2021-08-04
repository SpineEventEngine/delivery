/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.spine.internal.dependency.Flogger
import io.spine.internal.dependency.Grpc
import io.spine.internal.dependency.Log4j2
import io.spine.internal.dependency.Spine
import io.spine.internal.dependency.Testcontainers

dependencies {
    api(project(":base"))
    implementation(Spine.Stable.server)
    implementation(Spine.Stable.client)
    testImplementation(Spine.Stable.Test.server)
    testImplementation(Testcontainers.lib)
    testImplementation(Testcontainers.junitJupiter)
    testRuntimeOnly(Grpc.nettyShaded)
    testRuntimeOnly(Log4j2.slf4jBridge)
    testRuntimeOnly(Log4j2.core)
    testRuntimeOnly(Flogger.Runtime.log4J2)
}
