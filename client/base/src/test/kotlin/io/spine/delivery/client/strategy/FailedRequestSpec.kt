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
import java.util.function.Supplier
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`FailedRequest` should")
internal class FailedRequestSpec {

    private val first = IllegalStateException("The first failure.")
    private val last = IllegalArgumentException("The last failure.")
    private val exceptions: ImmutableList<RuntimeException> = ImmutableList.of(first, last)

    private fun failedRequest(retry: Supplier<String> = Supplier { RESULT }) =
        FailedRequest(retry, exceptions)

    @Test
    fun `reject an empty list of exceptions`() {
        shouldThrow<IllegalArgumentException> {
            FailedRequest(Supplier { RESULT }, ImmutableList.of<RuntimeException>())
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
        val action = failedRequest(Supplier { calls++; RESULT }).retry()
        calls shouldBe 0

        action.execute() shouldBe RESULT
        calls shouldBe 1
    }

    @Test
    fun `retry the request on each execution of the returned action`() {
        var calls = 0
        val action = failedRequest(Supplier { calls++; RESULT }).retry()

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
            failedRequest(Supplier { calls++; RESULT }).propagate()
        }

        calls shouldBe 0
    }

    private companion object {
        const val RESULT = "The result of the request."
    }
}
