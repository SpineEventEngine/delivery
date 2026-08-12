/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.dependency.lib

// https://commons.apache.org/proper/commons-compress/changes-report.html
/**
 * Used only on the *buildscript* classpath of the root project, to keep the Jib Gradle
 * plugin working.
 *
 * `io.spine.tools:intellij-platform`, which arrives transitively via the Spine Compiler
 * plugin, is an uber JAR bundling `org.apache.commons.compress.**` *without relocating*
 * it, at a version predating 1.26. Its copy shadows the real artifact, and Jib — compiled
 * against 1.26 — then fails with `NoSuchMethodError` on
 * `TarArchiveOutputStream.putArchiveEntry(TarArchiveEntry)`, an overload added in 1.26.
 *
 * The version must therefore stay at or above 1.26, matching what the Jib plugin declares.
 */
@Suppress("unused", "ConstPropertyName")
object CommonsCompress {
    private const val version = "1.26.0"
    const val lib = "org.apache.commons:commons-compress:$version"
}
