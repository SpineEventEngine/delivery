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

import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.spine.delivery.ReadMessagesSinceTime
import io.spine.delivery.server.WithApp
import io.spine.server.delivery.DeliveryStrategy.newIndex
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`InboxService` should")
internal class InboxServiceSpec : WithApp() {

    /**
     * Expects the `UNKNOWN` status, which the earlier versions of the server returned for
     * such a page size: their storage rejected it with an `IllegalArgumentException`, which
     * gRPC reports as `UNKNOWN`. The status stays, so the clients see no change.
     */
    @Test
    fun `fail a page request whose page size is not positive`() {
        for (pageSize in listOf(0, -1)) {
            val request = ReadMessagesSinceTime.newBuilder()
                .setShard(newIndex(1, 5))
                .setPageSize(pageSize)
                .buildPartial()

            val error = shouldThrow<StatusRuntimeException> {
                syncInboxService().findManyInShard(request)
            }

            error.status.code shouldBe Status.Code.UNKNOWN
        }
    }
}
