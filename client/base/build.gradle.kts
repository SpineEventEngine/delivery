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
import io.spine.dependency.test.Kotest

dependencies {
    // `ManagedChannel` appears in the public API of `AbstractDeliveryBootstrapper`.
    api(Grpc.core)
    // Command, event, and rejection types of the Delivery server appear in
    // the public API of the client contracts.
    api(project(":delivery-model"))
    testImplementation(Kotest.assertions)
}

apply {
    from(rootDir.toPath().resolve("test-artifacts.gradle"))
}
