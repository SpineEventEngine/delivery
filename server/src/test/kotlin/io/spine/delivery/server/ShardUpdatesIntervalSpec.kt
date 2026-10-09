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

package io.spine.delivery.server

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.Duration
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("The interval of the shard updates should")
internal class ShardUpdatesIntervalSpec {

    @Test
    fun `be 25 ms when the variable is not set`() {
        DeliveryServerApp.DEFAULT_SHARD_UPDATES_INTERVAL shouldBe Duration.ofMillis(25)
        DeliveryServerApp.shardUpdatesInterval(null) shouldBe Duration.ofMillis(25)
        DeliveryServerApp.shardUpdatesInterval("") shouldBe Duration.ofMillis(25)
    }

    @Test
    fun `be the given number of milliseconds`() {
        DeliveryServerApp.shardUpdatesInterval("100") shouldBe Duration.ofMillis(100)
    }

    @Test
    fun `be zero, which turns the throttling off`() {
        DeliveryServerApp.shardUpdatesInterval("0") shouldBe Duration.ZERO
    }

    @Test
    fun `reject a negative number, naming the variable`() {
        val error = shouldThrow<IllegalArgumentException> {
            DeliveryServerApp.shardUpdatesInterval("-1")
        }
        error.message shouldContain DeliveryServerApp.SHARD_UPDATES_INTERVAL_VARIABLE
    }

    @Test
    fun `reject a value that is not a whole number, naming the variable`() {
        for (value in listOf("25ms", "2.5", " 25")) {
            val error = shouldThrow<IllegalArgumentException> {
                DeliveryServerApp.shardUpdatesInterval(value)
            }
            error.message shouldContain DeliveryServerApp.SHARD_UPDATES_INTERVAL_VARIABLE
        }
    }
}
