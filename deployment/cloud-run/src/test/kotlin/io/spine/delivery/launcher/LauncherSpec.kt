/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.launcher

import io.kotest.matchers.shouldBe
import java.util.concurrent.Executors
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Verifies how [Launcher] prepares the server threads.
 *
 * The threads are only created here, never started: starting one would launch a real
 * gRPC server and block. What matters is their configuration, and in particular that
 * the Admin Server thread is a daemon one — were it not, the container would outlive
 * the Delivery server the launcher waits for.
 */
@DisplayName("`Launcher` should")
internal class LauncherSpec {

    private val threads = Executors.defaultThreadFactory()
    private val args = arrayOf<String>()

    @Test
    @DisplayName("prepare the Delivery server thread which keeps the JVM alive")
    fun deliveryThread() {
        val delivery = Launcher.delivery(threads, args)

        delivery.name shouldBe "delivery"
        delivery.isDaemon shouldBe false
        delivery.isAlive shouldBe false
    }

    @Test
    @DisplayName("prepare the Admin Server thread as a daemon one")
    fun adminThread() {
        val admin = Launcher.admin(threads, args)

        admin.name shouldBe "admin"
        admin.isDaemon shouldBe true
        admin.isAlive shouldBe false
    }

    /**
     * The Admin Server is off unless `ADMIN_SERVER` is set.
     *
     * Only this default is asserted: flipping the variable would require reflecting
     * into JDK internals, which this project does not allow in tests.
     */
    @Test
    @DisplayName("keep the Admin Server off by default")
    fun adminOffByDefault() {
        Launcher.useAdminServer() shouldBe false
    }
}
