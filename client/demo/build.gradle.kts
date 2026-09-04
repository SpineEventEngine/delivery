/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
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
