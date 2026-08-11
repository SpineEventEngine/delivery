/*
 * Copyright 2026, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
