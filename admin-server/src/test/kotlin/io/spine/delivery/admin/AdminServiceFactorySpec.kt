/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package io.spine.delivery.admin

import io.grpc.ManagedChannel
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Verifies where [AdminServiceFactory] connects.
 *
 * The suites that run the Admin Server replace the factory's stub with one connected to
 * a stand-in for the Delivery server, so they do not reach this wiring.
 */
@DisplayName("`AdminServiceFactory` should")
internal class AdminServiceFactorySpec {

    @Test
    fun `connect to the Delivery server on the same instance`() {
        val stub = AdminServiceFactory().blockingAdminService()
        val channel = stub.channel as ManagedChannel
        try {
            channel.authority() shouldBe "127.0.0.1:${deliveryServerPort()}"
        } finally {
            channel.shutdownNow()
        }
    }
}

/**
 * Returns the port of the Delivery server: the value of `PORT`, or `8484` if it is not set.
 */
private fun deliveryServerPort(): String =
    System.getenv("PORT")?.takeIf { it.isNotEmpty() } ?: "8484"
