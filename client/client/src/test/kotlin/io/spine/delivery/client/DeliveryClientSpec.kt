/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.client

import io.grpc.ManagedChannelBuilder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests the channel ownership of [DeliveryClient].
 *
 * These cases need no running server: a `ManagedChannel` connects lazily,
 * so its shutdown state is observable without any RPC being made.
 *
 * The RPC behavior of the client is covered by [DeliveryClientTest].
 */
@DisplayName("`DeliveryClient` should")
internal class DeliveryClientSpec {

    @Test
    fun `shut down the channel it created`() {
        val client = DeliveryClient.create("localhost", PORT)
        val channel = client.channel()
        try {
            channel.isShutdown shouldBe false

            client.close()

            channel.isShutdown shouldBe true
        } finally {
            channel.shutdownNow()
        }
    }

    @Test
    fun `leave a caller-supplied channel open when closed`() {
        val channel = ManagedChannelBuilder
            .forAddress("localhost", PORT)
            .usePlaintext()
            .build()
        try {
            val client = DeliveryClient.create(channel)

            client.close()

            channel.isShutdown shouldBe false
        } finally {
            channel.shutdownNow()
        }
    }

    private companion object {

        /**
         * A port nothing listens on; the channels in these tests never connect.
         */
        const val PORT = 8484
    }
}
