/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.spine.internal.dependency.Flogger
import io.spine.internal.dependency.Log4j2
import io.spine.internal.dependency.Grpc
import io.spine.internal.dependency.Testcontainers

dependencies {
    implementation(project(":simple-client"))
    testImplementation(Testcontainers.lib)
    testImplementation(Testcontainers.junitJupiter)
    testImplementation(project(":testutil-client"))
    testImplementation(project(path = ":base", configuration = "testArtifacts"))
    testRuntimeOnly(Grpc.nettyShaded)
    testRuntimeOnly(Log4j2.slf4jBridge)
    testRuntimeOnly(Log4j2.core)
    testRuntimeOnly(Flogger.Runtime.log4J2)
}

tasks.test {
    useJUnitPlatform {
        excludeTags("integration")
    }
}
