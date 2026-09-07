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

// Conventions (Java toolchain, CoreJvm codegen, dependency management, publishing) come
// from the `module` script plugin. This container project has no sources of its own — it
// only aggregates the client modules — but it applies `module` because the conventions
// reached it when they lived in the root `subprojects {}` block, and `defineDependencies()`
// skips projects without a `src` directory anyway.
plugins {
    module
}
