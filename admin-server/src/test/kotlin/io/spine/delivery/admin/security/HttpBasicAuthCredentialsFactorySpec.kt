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

package io.spine.delivery.admin.security

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`HttpBasicAuthCredentialsFactory` should")
internal class HttpBasicAuthCredentialsFactorySpec {

    @Test
    fun `fall back to default credentials when the environment variables are not set`() {
        val credentials = HttpBasicAuthCredentialsFactory().validCredentials()

        credentials.username() shouldBe DEFAULT
        credentials.password() shouldBe DEFAULT
    }

    private companion object {

        /**
         * The value of both the default username and the default password.
         *
         * The test assumes the `ADMIN_USERNAME` and `ADMIN_PASSWORD` environment
         * variables are not set in the JVM running the tests.
         */
        const val DEFAULT = "admin"
    }
}
