/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.spine.dependency.lib.Grpc
import io.spine.dependency.test.Testcontainers

dependencies {
    api(project(":client:delivery-client-base"))
    // The gRPC stubs of the `Inbox` and `Shard` services.
    implementation(project(":grpc-api"))
    testImplementation(project(":fixtures"))
    testImplementation(project(path = ":client:delivery-client-base",
                               configuration = "testArtifacts"))
    testImplementation(Testcontainers.lib)
    testImplementation(Testcontainers.junitJupiter)
    testRuntimeOnly(Grpc.nettyShaded)
}

// The `integration`-tagged suites run the Delivery server from a Docker image. They are
// no longer excluded: `checkDockerAvailable` enforces Docker, and each suite is annotated
// `@RequiresDeliveryImage`, which skips it when the image is absent.
