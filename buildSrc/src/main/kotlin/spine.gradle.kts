/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import com.google.protobuf.gradle.protoc
import io.spine.internal.dependency.Protobuf
import io.spine.internal.dependency.Spine
import org.gradle.plugins.ide.idea.model.Module
import org.gradle.plugins.ide.idea.model.ModuleLibrary

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
    implementation(Spine.base)
    testImplementation(Spine.Test.base)
}

protobuf.protobuf.protoc {
    artifact = Protobuf.compiler
}
