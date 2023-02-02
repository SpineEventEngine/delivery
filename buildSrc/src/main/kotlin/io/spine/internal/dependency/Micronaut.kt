/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

object Micronaut {

    const val version = "3.8.3";
    const val runtime = "io.micronaut:micronaut-runtime"
    const val reactor = "io.micronaut.reactor:micronaut-reactor"
    const val security = "io.micronaut.security:micronaut-security"
    const val httpClient = "io.micronaut.reactor:micronaut-reactor-http-client"

    object AnnotationProcessor {
        const val httpValidation = "io.micronaut:micronaut-http-validation"
        const val security = "io.micronaut.security:micronaut-security-annotations"
    }

    object Test {
        const val core = "io.micronaut.test:micronaut-test-core"
        const val jUnit5 = "io.micronaut.test:micronaut-test-junit5"
    }
}
