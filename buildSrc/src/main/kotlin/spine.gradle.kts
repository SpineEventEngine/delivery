/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.spine.internal.dependency.Protobuf
import io.spine.internal.dependency.Spine
import io.spine.tools.protoc.MessageSelectorFactory.suffix
import org.gradle.plugins.ide.idea.model.Module
import org.gradle.plugins.ide.idea.model.ModuleDependency

plugins {
    `java-library`
    idea
    id("com.google.protobuf")
    id("io.spine.mc-java")
}


val generatedRootDir = "${projectDir}/generated"
val generatedSpineDir = file("${generatedRootDir}/main/spine")
val generatedTestSpineDir = file("${generatedRootDir}/test/spine")

sourceSets {
    main {
        java {
            srcDirs.add(generatedSpineDir)
        }
    }
    test {
        java {
            srcDirs.add(generatedTestSpineDir)
        }
    }
}

idea {
    module {
        sourceDirs.add(generatedSpineDir)
        generatedSourceDirs.add(generatedSpineDir)
        testSourceDirs.add(generatedTestSpineDir)
        iml {
            beforeMerged(Action<Module> {
                dependencies.clear()
            })
            whenMerged(Action<Module> {
                dependencies.forEach {
                    (it as ModuleDependency).isExported = true
                }
            })
        }
    }
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
