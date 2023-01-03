/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

/*
 * This plugin enables the new Javadoc tags in the build.
 *
 * The tags are:
 * 1. @apiNote - the additional notes and commentaries regarding the API
 * 2. @implSpec - the implementation specification
 * 3. @implNote - the commentaries and notes about the implementation
 *
 * It also explicitly states the encoding of the source files from which the Javadoc is composed,
 * ensuring correct execution of the `javadoc` task.
 *
 * For the detailed description of the new tags, see:
 * https://blog.codefx.org/java/new-javadoc-tags/#apiNote-implSpec-and-implNote
 *
 * This script should be applied to the `subprojects` section of the root project,
 * or to the specific child projects.
 */

plugins {
    `java-library`
}

object JavadocOptions {

    const val encoding = "UTF-8"
    val tags = setOf(
        "apiNote:a:API Note:",
        "implSpec:a:Implementation Requirements:",
        "implNote:a:Implementation Note:"
    )
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).tags?.addAll(JavadocOptions.tags)
    (options as StandardJavadocDocletOptions).encoding = JavadocOptions.encoding
}

if (JavaVersion.current().isJava8Compatible) {
    tasks.javadoc {
        (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
    }
}

if (JavaVersion.current().isJava11Compatible) {
    tasks.javadoc {
        (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
    }
}
