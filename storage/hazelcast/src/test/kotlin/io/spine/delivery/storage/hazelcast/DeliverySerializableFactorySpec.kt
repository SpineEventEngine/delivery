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

package io.spine.delivery.storage.hazelcast

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeUnique
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`DeliverySerializableFactory` should")
internal class DeliverySerializableFactorySpec {

    private val factory = DeliverySerializableFactory()

    @Test
    fun `know classes with distinct IDs`() {
        ClassId.entries.map { it.id }.shouldBeUnique()
    }

    @Test
    fun `create an instance of each class that reports its ID`() {
        for (classId in ClassId.entries) {
            val created = factory.create(classId.id)

            created.factoryId shouldBe FACTORY_ID
            created.classId shouldBe classId.id
        }
    }

    @Test
    fun `reject an unknown class ID`() {
        shouldThrow<IllegalArgumentException> { factory.create(0) }
    }
}
