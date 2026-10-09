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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.spine.delivery.storage.given.session
import io.spine.delivery.storage.given.shard
import java.util.UUID
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("The stored form of a session record should")
internal class StoredFormsSpec {

    @Test
    fun `keep the record and its write ID`() {
        val record = session(shard(1))
        val writeId = UUID.randomUUID()
        val form = sessionForm(writeId, record)

        val stored = parseSession(form)

        stored.record shouldBe record
        stored.writeId shouldBe writeId
        stored.form shouldBe form
    }

    @Test
    fun `be the bytes of a 'StoredShardSession'`() {
        val record = session(shard(1))
        val writeId = UUID.randomUUID()

        val session = StoredShardSession.parseFrom(sessionForm(writeId, record))

        session.writeId shouldBe writeId.toString()
        session.record shouldBe record
    }

    @Test
    fun `not be parsed from bytes that are not a 'StoredShardSession'`() {
        shouldThrow<IllegalStateException> { parseSession(byteArrayOf(-1)) }
    }

    @Test
    fun `not be parsed with a write ID that is not a UUID`() {
        val form = StoredShardSession.newBuilder()
            .setWriteId("not a UUID")
            .setRecord(session(shard(1)))
            .build()
            .toByteArray()

        shouldThrow<IllegalStateException> { parseSession(form) }
    }
}
