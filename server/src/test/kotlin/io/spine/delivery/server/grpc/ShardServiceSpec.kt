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

package io.spine.delivery.server.grpc

import com.google.protobuf.Empty
import com.google.protobuf.util.Durations
import io.grpc.Context
import io.grpc.Status
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.spine.delivery.DeliveryPickUpOutcome
import io.spine.delivery.server.DeliveryShardRegistry
import io.spine.delivery.server.given.RacingShardSessionStore
import io.spine.delivery.server.grpc.given.ShardServiceTestEnv.byAnotherWorker
import io.spine.delivery.server.grpc.given.ShardServiceTestEnv.pickUpShard
import io.spine.delivery.server.grpc.given.ShardServiceTestEnv.release
import io.spine.delivery.storage.memory.InMemoryShardSessionStore
import io.spine.grpc.MemoizingObserver
import io.spine.grpc.StreamObservers.memoizingObserver
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`ShardService` should")
internal class ShardServiceSpec {

    private val service = ShardService(InMemoryShardSessionStore(), Durations.ZERO)

    @Test
    fun `not pick up a shard for a cancelled call`() {
        val request = pickUpShard()
        val cancelled = memoizingObserver<DeliveryPickUpOutcome>()

        inCancelledContext { service.pickShard(request, cancelled) }

        cancelled.shouldBeCancelled()
        val next = memoizingObserver<DeliveryPickUpOutcome>()
        service.pickShard(byAnotherWorker(request), next)
        next.firstResponse().hasPickedUp() shouldBe true
    }

    @Test
    fun `not release a shard for a cancelled call`() {
        val request = pickUpShard()
        service.pickShard(request, memoizingObserver())
        val cancelled = memoizingObserver<Empty>()

        inCancelledContext { service.releaseSession(release(request), cancelled) }

        cancelled.shouldBeCancelled()
        val next = memoizingObserver<DeliveryPickUpOutcome>()
        service.pickShard(byAnotherWorker(request), next)
        next.firstResponse().hasAlreadyPickedUp() shouldBe true
    }

    @Test
    fun `keep the session that the worker renews while its release is in progress`() {
        val shared = InMemoryShardSessionStore()
        val other = DeliveryShardRegistry(shared, Durations.ZERO)
        val request = pickUpShard()
        other.pickUp(request.shard, request.worker)
        val racing = RacingShardSessionStore(shared, beforeFirstWrite = {
            other.pickUp(it, request.worker)
        })
        val releasing = ShardService(racing, Durations.ZERO)
        val released = memoizingObserver<Empty>()

        releasing.releaseSession(release(request), released)

        released.isCompleted shouldBe true
        released.error.shouldBeNull()
        val next = memoizingObserver<DeliveryPickUpOutcome>()
        releasing.pickShard(byAnotherWorker(request), next)
        next.firstResponse().hasAlreadyPickedUp() shouldBe true
    }

    /**
     * Runs the given action in a cancelled gRPC context, as gRPC runs a call that is
     * cancelled before it is served.
     */
    private fun inCancelledContext(action: () -> Unit) {
        val context = Context.current().withCancellation()
        context.cancel(Status.CANCELLED.withDescription("RPC cancelled").asRuntimeException())
        context.run(Runnable { action() })
    }

    /**
     * Asserts that the call was ended with the `CANCELLED` status, and without any response.
     */
    private fun MemoizingObserver<*>.shouldBeCancelled() {
        Status.fromThrowable(error.shouldNotBeNull()).code shouldBe Status.Code.CANCELLED
        responses().shouldBeEmpty()
    }
}
