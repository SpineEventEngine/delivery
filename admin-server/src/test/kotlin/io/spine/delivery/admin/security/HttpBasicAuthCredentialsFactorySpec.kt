/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
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
