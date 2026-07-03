/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.spine.internal.dependency.CheckerFramework
import io.spine.internal.dependency.ErrorProne
import io.spine.internal.dependency.FindBugs
import io.spine.internal.dependency.Guava
import io.spine.internal.dependency.JUnit
import io.spine.internal.dependency.JavaX
import io.spine.internal.dependency.Truth
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    `java-library`
    id("net.ltgt.errorprone")
}

java {
    // The Spine SDK line consumed here (base 2.0.0-SNAPSHOT.421 / core .381) publishes
    // artifacts that require Java 17 or newer.
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    errorprone(ErrorProne.core)
    errorprone(ErrorProne.Plugin.nullaway)
    compileOnlyApi(CheckerFramework.annotations)
    compileOnlyApi(FindBugs.annotations)
    compileOnlyApi(JavaX.annotations)
    ErrorProne.annotations.forEach { compileOnlyApi(it) }

    implementation(Guava.lib)
    testImplementation(Guava.testLib)
    testImplementation(enforcedPlatform(JUnit.bom))
    JUnit.api.forEach { testImplementation(it) }
    Truth.libs.forEach { testImplementation(it) }
    testRuntimeOnly(JUnit.engine)
    // Gradle 9 no longer bundles the JUnit Platform launcher on the test runtime
    // classpath; it must be declared explicitly. Version is managed by `JUnit.bom`.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform {
        includeEngines("junit-jupiter")
    }

    testLogging {
        events = setOf(TestLogEvent.FAILED, TestLogEvent.SKIPPED)
        exceptionFormat = TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

tasks.compileJava {
    // Explicitly states the encoding of the source and test source files, ensuring
    // correct execution of the `javac` task.
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation", "-Werror"))

    // Configure Error Prone:
    // 1. Exclude generated sources from being analyzed by Error Prone.
    // 2. Turn the check off until Error Prone can handle `@Nested` JUnit classes.
    //    See issue: https://github.com/google/error-prone/issues/956
    // 3. Turn off checks which report unused methods and unused method parameters.
    //    See issue: https://github.com/SpineEventEngine/config/issues/61
    //
    // For more config details see:
    //    https://github.com/tbroyer/gradle-errorprone-plugin/tree/master#usage
    options.errorprone.errorproneArgs.addAll(
        listOf(
            "-XepExcludedPaths:.*/generated/.*",
            "-Xep:ClassCanBeStatic:OFF",
            "-Xep:UnusedMethod:OFF",
            "-Xep:UnusedVariable:OFF",
            "-Xep:CheckReturnValue:OFF"
        )
    )

    options.errorprone {
        option("NullAway:AnnotatedPackages", "io.spine.delivery")
        disableWarningsInGeneratedCode.set(true)
    }
}

tasks.compileTestJava {
    options.errorprone.enabled.set(false)
}
