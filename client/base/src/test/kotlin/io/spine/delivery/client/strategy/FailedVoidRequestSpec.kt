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

package io.spine.delivery.client.strategy

import com.google.common.collect.ImmutableList
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.spine.delivery.client.ExecutionFailedException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`FailedVoidRequest` should")
internal class FailedVoidRequestSpec {

    private val first = IllegalStateException("The first failure.")
    private val last = IllegalArgumentException("The last failure.")
    private val exceptions: ImmutableList<RuntimeException> = ImmutableList.of(first, last)

    private fun failedRequest(retry: Runnable = Runnable { }) =
        FailedVoidRequest(retry, exceptions)

    @Test
    fun `reject an empty list of exceptions`() {
        shouldThrow<IllegalArgumentException> {
            FailedVoidRequest(Runnable { }, ImmutableList.of<RuntimeException>())
        }
    }

    @Test
    fun `expose all occurred exceptions in the order of their occurrence`() {
        failedRequest().allExceptions() shouldContainExactly listOf(first, last)
    }

    @Test
    fun `expose the last occurred exception`() {
        failedRequest().lastException() shouldBe last
    }

    @Test
    fun `retry the request only when the returned action is executed`() {
        var calls = 0
        val action = failedRequest(Runnable { calls++ }).retry()
        calls shouldBe 0

        action.execute()

        calls shouldBe 1
    }

    @Test
    fun `retry the request on each execution of the returned action`() {
        var calls = 0
        val action = failedRequest(Runnable { calls++ }).retry()

        action.execute()
        action.execute()

        calls shouldBe 2
    }

    @Test
    fun `propagate all occurred exceptions`() {
        val error = shouldThrow<ExecutionFailedException> {
            failedRequest().propagate()
        }

        error.causes() shouldContainExactly listOf(first, last)
    }

    @Test
    fun `propagate without retrying the request`() {
        var calls = 0

        shouldThrow<ExecutionFailedException> {
            failedRequest(Runnable { calls++ }).propagate()
        }

        calls shouldBe 0
    }
}
