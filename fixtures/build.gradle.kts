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
import io.spine.dependency.local.CoreJvm
import io.spine.dependency.test.JUnit
import io.spine.dependency.test.Testcontainers

plugins {
    module
}

/**
 * Test fixtures shared by the client and the server test suites.
 *
 * The module deliberately depends on neither, so that both can use it.
 */
dependencies {
    // `TestInboxMessages` builds `InboxMessage`s via `TestActorRequestFactory`.
    api(CoreJvm.serverTestLib)
    // `NoOpChannel` extends `ManagedChannel`.
    api(Grpc.core)
    // `RequiresDeliveryImage` is a JUnit extension; `DeliveryImage` returns
    // a Testcontainers `DockerImageName`.
    api(JUnit.Jupiter.api)
    api(Testcontainers.lib)
}
