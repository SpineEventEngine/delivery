/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
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
