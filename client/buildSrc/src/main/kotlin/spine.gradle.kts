/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import com.google.protobuf.gradle.protoc
import io.spine.internal.dependency.Protobuf
import io.spine.internal.dependency.Spine
import io.spine.tools.protoc.MessageSelectorFactory.suffix
import org.gradle.plugins.ide.idea.model.Module
import org.gradle.plugins.ide.idea.model.ModuleLibrary

plugins {
    `java-library`
    idea
    id("com.google.protobuf")
    id("io.spine.tools.spine-model-compiler")
}


val generatedRootDir = "${projectDir}/generated"
val generatedSpineDir = file("${generatedRootDir}/main/spine")
val generatedTestSpineDir = file("${generatedRootDir}/test/spine")

sourceSets {
    main {
        java {
            srcDir(generatedSpineDir)
        }
    }
    test {
        java {
            srcDir(generatedTestSpineDir)
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
                    (it as ModuleLibrary).isExported = true
                }
            })
        }
    }
}

dependencies {
    Protobuf.libs.forEach { implementation(it) }
    implementation(Spine.Stable.base)
    testImplementation(Spine.Stable.Test.base)
}

modelCompiler {
    generateValidation = true
    columns {
        generate(true)
    }

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
        applyFactory("io.spine.code.gen.java.UuidMethodFactory", messages().uuid())
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
        generateFor(messages().entityState(), markAs("io.spine.base.EntityStateField"))
    }
}

protobuf.protobuf.protoc {
    artifact = Protobuf.compiler
}
