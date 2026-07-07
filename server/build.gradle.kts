/*
 * Copyright 2026, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import io.spine.dependency.lib.Grpc
import io.spine.dependency.lib.Log4j2
import io.spine.dependency.local.CoreJvm
import io.spine.dependency.local.Time

dependencies {
    implementation(Log4j2.core)
    implementation(project(":model"))
    implementation(project(":grpc-api"))
    implementation(project(":storage:redis"))
    implementation(Grpc.core)
    implementation(Grpc.inProcess)
    implementation(CoreJvm.server)
    testImplementation(project(":testutil-server"))
    testImplementation(project(path = ":grpc-api", configuration = "testArtifacts"))
    testImplementation(Time.testLib)
    testRuntimeOnly(Grpc.nettyShaded)

    // Use this one to run the app locally.
//    implementation(Grpc.nettyShaded)
}
