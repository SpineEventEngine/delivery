/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.spine.dependency.lib.Grpc
import io.spine.dependency.lib.JavaX

dependencies {
    implementation(project(":client:delivery-client"))
    // The in-process transport hosting the demo server.
    implementation(Grpc.inProcess)
    // The servlet container of the deployment module provides the API at runtime.
    compileOnly(JavaX.servletApi)
    // A gRPC transport for connecting to the Delivery server.
    runtimeOnly(Grpc.nettyShaded)
}
