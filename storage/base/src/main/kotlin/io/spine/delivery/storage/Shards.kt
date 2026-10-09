/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

@file:JvmName("Shards")

package io.spine.delivery.storage

import io.spine.server.delivery.ShardIndex

/**
 * The separator of the numbers in a shard tag.
 */
private const val TAG_SEPARATOR = '/'

/**
 * Returns the tag of the shard: `<index>/<ofTotal>`, in decimal.
 *
 * The tag identifies the shard where only a string can, such as in the key of a database
 * entry, or in a message about a changed shard.
 */
public fun ShardIndex.tag(): String = "$index$TAG_SEPARATOR$ofTotal"

/**
 * Returns the shard that the given [tag][ShardIndex.tag] identifies.
 *
 * @throws IllegalArgumentException If the string is not a shard tag.
 */
public fun shardOf(tag: String): ShardIndex {
    val separator = tag.indexOf(TAG_SEPARATOR)
    require(separator > 0) { "`$tag` is not a shard tag." }
    val index = tag.substring(0, separator).toIntOrNull()
    val ofTotal = tag.substring(separator + 1).toIntOrNull()
    require(index != null && ofTotal != null) { "`$tag` is not a shard tag." }
    return ShardIndex.newBuilder()
        .setIndex(index)
        .setOfTotal(ofTotal)
        .buildPartial()
}
