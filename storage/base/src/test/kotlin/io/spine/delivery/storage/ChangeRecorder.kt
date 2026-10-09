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

package io.spine.delivery.storage

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.spine.server.delivery.ShardIndex
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.function.Consumer
import org.junit.jupiter.api.fail

/**
 * Collects the shards that a store reports as changed.
 *
 * @param changeTimeout How long to wait for a change that must be reported.
 * @param quietPeriod How long to wait to make sure that no other change is reported;
 *   zero for a store that reports changes before its operation returns.
 */
public class ChangeRecorder(
    private val changeTimeout: Duration,
    private val quietPeriod: Duration
) : Consumer<ShardIndex> {

    /**
     * The reported shards, in the order they were reported.
     */
    private val changes = LinkedBlockingQueue<ShardIndex>()

    override fun accept(shard: ShardIndex) {
        changes.add(shard)
    }

    /**
     * Waits until each of the given shards is reported, and checks that no other
     * shard is.
     *
     * A shard reported more than once counts once: a backend may report one change in
     * several parts.
     */
    public fun expect(vararg shards: ShardIndex) {
        val expected = shards.toSet()
        val received = HashSet<ShardIndex>()
        val deadline = System.nanoTime() + changeTimeout.toNanos()
        while (!received.containsAll(expected)) {
            val left = deadline - System.nanoTime()
            val shard = changes.poll(left, NANOSECONDS)
                ?: fail { "Expected changes of $expected, but received only $received." }
            received.add(shard)
        }
        received.addAll(drainAfterQuietPeriod())
        received shouldBe expected
    }

    /**
     * Checks that no shard is reported.
     */
    public fun expectNone() {
        drainAfterQuietPeriod().shouldBeEmpty()
    }

    /**
     * Waits for the quiet period, and then takes all the reported shards.
     */
    private fun drainAfterQuietPeriod(): List<ShardIndex> {
        if (!quietPeriod.isZero) {
            Thread.sleep(quietPeriod.toMillis())
        }
        val rest = ArrayList<ShardIndex>()
        changes.drainTo(rest)
        return rest
    }
}
