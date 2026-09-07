/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.spine.dependency.lib

// https://commons.apache.org/proper/commons-compress/
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
