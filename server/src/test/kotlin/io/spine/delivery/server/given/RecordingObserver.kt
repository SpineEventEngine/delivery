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

package io.spine.delivery.server.given

import io.grpc.stub.ServerCallStreamObserver
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.spine.delivery.admin.grpc.ShardInfoUpdate
import io.spine.delivery.admin.grpc.SubscriptionResponse
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.MILLISECONDS
import org.junit.jupiter.api.fail

/**
 * How long a test waits for an expected update.
 */
private val UPDATE_TIMEOUT: Duration = Duration.ofSeconds(10)

/**
 * How long a test waits to make sure that no update arrives.
 */
private val QUIET_PERIOD: Duration = Duration.ofMillis(200)

/**
 * The server side of an admin subscription, recording what the server sends.
 *
 * @param failingUpdates whether sending an update to this subscriber fails
 */
internal class RecordingObserver(
    private val failingUpdates: Boolean = false
) : ServerCallStreamObserver<SubscriptionResponse>() {

    private val responses = LinkedBlockingQueue<SubscriptionResponse>()
    private var onCancel: Runnable? = null

    /**
     * The error the server closed the subscription with, if any.
     */
    @Volatile
    var error: Throwable? = null
        private set

    override fun onNext(value: SubscriptionResponse) {
        check(!(failingUpdates && value.hasUpdate())) { "The subscriber is gone." }
        responses.add(value)
    }

    override fun onError(t: Throwable) {
        error = t
    }

    override fun onCompleted() = Unit

    override fun isCancelled(): Boolean = false

    override fun setOnCancelHandler(onCancelHandler: Runnable) {
        onCancel = onCancelHandler
    }

    override fun setCompression(compression: String) = Unit

    override fun isReady(): Boolean = true

    override fun setOnReadyHandler(onReadyHandler: Runnable) = Unit

    @Deprecated("Deprecated in gRPC")
    override fun disableAutoInboundFlowControl() = Unit

    override fun request(count: Int) = Unit

    override fun setMessageCompression(enable: Boolean) = Unit

    /**
     * Cancels the subscription, as a client that goes away does.
     */
    fun cancel() {
        onCancel?.run()
    }

    /**
     * Returns the next response, waiting for it.
     */
    fun nextResponse(): SubscriptionResponse =
        responses.poll(UPDATE_TIMEOUT.toMillis(), MILLISECONDS)
            ?: fail { "No response arrived within $UPDATE_TIMEOUT." }

    /**
     * Returns the next update, waiting for it, and skipping the acknowledgment.
     */
    fun nextUpdate(): ShardInfoUpdate {
        var response = nextResponse()
        if (response.hasCreated()) {
            response = nextResponse()
        }
        response.hasUpdate() shouldBe true
        return response.update
    }

    /**
     * Returns the given number of next updates, waiting for them.
     */
    fun nextUpdates(count: Int): List<ShardInfoUpdate> = (1..count).map { nextUpdate() }

    /**
     * Returns the next update with the given state, skipping the others, and waiting for it.
     */
    fun awaitUpdate(expected: ShardInfoUpdate): List<ShardInfoUpdate> {
        val received = ArrayList<ShardInfoUpdate>()
        while (received.lastOrNull() != expected) {
            received.add(nextUpdate())
        }
        return received
    }

    /**
     * Checks that no update arrives within a short period.
     */
    fun expectNoUpdate() {
        var response = responses.poll(QUIET_PERIOD.toMillis(), MILLISECONDS)
        if (response != null && response.hasCreated()) {
            response = responses.poll(QUIET_PERIOD.toMillis(), MILLISECONDS)
        }
        response.shouldBeNull()
    }
}
