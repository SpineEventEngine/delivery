/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.server

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.TimeUnit.MILLISECONDS
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.parallel.Isolated

/**
 * How long the test waits for the server thread to finish reporting the startup failure.
 *
 * Deliberately shorter than the `@Timeout` of the tests, so that a thread that never
 * finishes fails on the assertion that follows the `join`, rather than on the JUnit timeout.
 */
private const val REPORTING_TIMEOUT_MILLIS = 2_000L

/**
 * Verifies how [SimpleApp] reports a gRPC server that fails to start.
 *
 * The happy path is covered by every suite extending [WithApp]. This one pins the
 * failure path: without it, a caller of `awaitPort()` would wait out the startup
 * timeout and see a generic message instead of the actual cause.
 */
@Isolated
@DisplayName("`SimpleApp`, when the gRPC server cannot start, should")
internal class SimpleAppStartupSpec {

    /**
     * The timeout is deliberately shorter than `SimpleApp.STARTUP_TIMEOUT_SECONDS`,
     * so that reporting the failure only after the full wait fails this test.
     */
    @Test
    @Timeout(5)
    @DisplayName("report the cause rather than waiting for the startup timeout")
    fun reportStartupFailure() {
        ServerSocket(0).use { occupied ->
            val app = SimpleApp(occupied.localPort)
            val serverThread = Thread(app::initAndStart)
            serverThread.start()

            val error = assertThrows<IllegalStateException> { app.awaitPort() }

            error.message shouldContain "failed to start"
            // The port is taken, so binding fails with `BindException`, wrapped by gRPC.
            error.cause.shouldBeInstanceOf<IOException>()

            // `awaitPort()` returns as soon as the server thread records the failure, while
            // that thread is still logging it. Waiting for the thread keeps the log entry
            // within this test instead of leaking into whichever test runs next.
            serverThread.join(REPORTING_TIMEOUT_MILLIS)
            serverThread.isAlive shouldBe false
        }
    }

    @Test
    @Timeout(5)
    @DisplayName("report the timeout when the server does not start at all")
    fun reportStartupTimeout() {
        // The app is never started, so the port is never assigned.
        val app = SimpleApp(0)

        val error = assertThrows<IllegalStateException> {
            app.awaitPort(50, MILLISECONDS)
        }

        error.message shouldContain "has not started within 50 milliseconds"
    }
}
