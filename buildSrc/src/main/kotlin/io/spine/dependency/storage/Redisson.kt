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

package io.spine.dependency.storage

/**
 * [Redisson](https://redisson.org/) — the Redis Java client backing
 * the `storage:redis` module.
 *
 * This file is owned by this repository — the `config` distribution
 * does not declare Redisson.
 */
@Suppress("unused", "ConstPropertyName")
object Redisson {
    // https://central.sonatype.com/artifact/org.redisson/redisson
    const val version = "4.7.0"
    const val lib = "org.redisson:redisson:$version"

    /**
     * Versions of the third-party libraries that this graph brings in, and that win
     * the cross-stack conflicts on the `delivery-server-cloud-run` classpath, where the
     * Redisson graph meets the Micronaut one. Update together with [version].
     */
    const val byteBuddyVersion = "1.18.2"
    const val snakeYamlVersion = "2.6"
}
