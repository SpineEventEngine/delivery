/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
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
