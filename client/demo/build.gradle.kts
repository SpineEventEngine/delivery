/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */
import io.spine.internal.dependency.Flogger
import io.spine.internal.dependency.Grpc
import io.spine.internal.dependency.Log4j2
import io.spine.internal.dependency.Spine

plugins {
    war
    spine
}

dependencies {
    runtimeOnly(Grpc.nettyShaded)
    runtimeOnly(Log4j2.slf4jBridge)
    runtimeOnly(Log4j2.core)
    runtimeOnly(Flogger.Runtime.log4J2)
    implementation(project(":client"))
    implementation(Log4j2.api)
    implementation(Spine.Stable.server)
    implementation(Spine.Stable.client)
    implementation("com.google.appengine:appengine-api-1.0-sdk:+")  // Latest App Engine APIs.
    providedCompile("javax.servlet:javax.servlet-api:3.1.0")
}
