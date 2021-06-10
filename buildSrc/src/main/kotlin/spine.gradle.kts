/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.spine.internal.dependency.Protobuf
import io.spine.internal.dependency.Spine
import io.spine.tools.protoc.MessageSelectorFactory.suffix

plugins {
    `java-library`
    id("com.google.protobuf")
    id("io.spine.mc-java")
}

dependencies {
    Protobuf.libs.forEach { implementation(it) }
    implementation(Spine.base)
    testImplementation(Spine.Test.base)
}

modelCompiler {
    generateValidation = true

    interfaces {
        mark(messages().inFiles(suffix("commands.proto")), asType("io.spine.base.CommandMessage"))
        mark(messages().inFiles(suffix("events.proto")), asType("io.spine.base.EventMessage"))
        mark(
            messages().inFiles(suffix("rejections.proto")),
            asType("io.spine.base.RejectionMessage")
        )
        mark(messages().uuid(), asType("io.spine.base.UuidValue"))
        mark(messages().entityState(), asType("io.spine.base.EntityState"))
    }

    methods {
        applyFactory("io.spine.tools.java.code.UuidMethodFactory", messages().uuid())
    }

    entityQueries {
        generate(true)
    }

    fields {
        generateFor(
            messages().inFiles(suffix("events.proto")),
            markAs("io.spine.base.EventMessageField")
        )
        generateFor(
            messages().inFiles(suffix("rejections.proto")),
            markAs("io.spine.base.EventMessageField")
        )
        generateFor(messages().entityState(), markAs("io.spine.query.EntityStateField"))
    }
}
