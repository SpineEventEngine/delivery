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
import io.micronaut.http.HttpRequest
import io.micronaut.security.authentication.AuthenticationRequest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`HttpBasicAuthProvider` should")
internal class HttpBasicAuthProviderSpec {

    private val provider =
        HttpBasicAuthProvider<Any>(HttpBasicAuthCredentials(USERNAME, PASSWORD))

    @Test
    fun `accept matching credentials`() {
        val response = provider.authenticate(GET, request(USERNAME, PASSWORD))

        response.isAuthenticated shouldBe true
        response.authentication.get().name shouldBe USERNAME
    }

    @Test
    fun `reject a wrong password`() {
        val response = provider.authenticate(GET, request(USERNAME, "wrong"))

        response.isAuthenticated shouldBe false
    }

    @Test
    fun `reject a wrong username`() {
        val response = provider.authenticate(GET, request("intruder", PASSWORD))

        response.isAuthenticated shouldBe false
    }

    private companion object {

        /** A request the provider does not inspect. */
        val GET: HttpRequest<Any> = HttpRequest.GET("/")

        const val USERNAME = "user"
        const val PASSWORD = "password"

        /**
         * Creates a stub authentication request with the given credentials.
         */
        fun request(identity: String, secret: String) =
            object : AuthenticationRequest<String, String> {
                override fun getIdentity() = identity
                override fun getSecret() = secret
            }
    }
}
