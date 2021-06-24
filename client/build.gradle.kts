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
    idea
    `java-convention`
    `dependency-management`
    `code-quality`
    spine
    war
    id("com.google.cloud.tools.appengine-appenginewebxml")
}

/** The GCP project ID used for deployment of the application. **/
//val gcpProject: String by project
val gcpProject = "bko-dev-firestore"

apply(from = "$rootDir/../version.gradle.kts")
group = "io.spine.message-delivery"
version = extra["messageDeliveryVersion"]!!

dependencies {
    runtimeOnly(Grpc.nettyShaded)
    runtimeOnly(Log4j2.slf4jBridge)
    runtimeOnly(Log4j2.core)
    runtimeOnly(Flogger.Runtime.log4J2)
    implementation(Log4j2.api)
    implementation(Spine.Stable.server)
    implementation(Spine.Stable.client)
    implementation("com.google.appengine:appengine-api-1.0-sdk:+")  // Latest App Engine Api's
    providedCompile("javax.servlet:javax.servlet-api:3.1.0")
}

// We're explicitly copying protos to ensure rejections are generated.
// See https://github.com/SpineEventEngine/base/issues/650 for details.
val copyExternalProtos = tasks.create<Copy>("copyExternalProtos") {
    from("../model/src/main/proto")
    into("./src/main/proto")
}

tasks.withType<com.google.protobuf.gradle.GenerateProtoTask> {
    dependsOn(copyExternalProtos)
}

appengine {
    deploy {
        projectId = gcpProject
        version = "1"
    }
}
