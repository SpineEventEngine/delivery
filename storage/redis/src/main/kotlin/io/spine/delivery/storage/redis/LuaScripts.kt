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

package io.spine.delivery.storage.redis

import org.redisson.api.RScript
import org.redisson.api.RedissonClient
import org.redisson.client.RedisNoScriptException
import org.redisson.client.codec.ByteArrayCodec

/**
 * A Lua script, run with `EVALSHA`, and loaded again when Redis no longer has it.
 *
 * Arguments and results are bytes. A script receives every key it uses through `KEYS`.
 */
internal class LuaScript(client: RedissonClient, private val source: String) {

    private val script = client.getScript(ByteArrayCodec.INSTANCE)

    @Volatile
    private var sha: String? = null

    /**
     * Runs the script, and returns the elements of the array it returns: byte arrays for
     * strings, and `Long`s for integers.
     */
    fun run(mode: RScript.Mode, keys: List<String>, args: List<ByteArray>): List<Any?> {
        val values = args.toTypedArray<Any>()
        val loaded = sha ?: load()
        return try {
            evalSha(mode, loaded, keys, values)
        } catch (_: RedisNoScriptException) {
            evalSha(mode, load(), keys, values)
        }
    }

    private fun evalSha(
        mode: RScript.Mode,
        digest: String,
        keys: List<String>,
        values: Array<Any>
    ): List<Any?> =
        script.evalSha<List<Any?>>(mode, digest, RScript.ReturnType.LIST, keys, *values)
            ?: emptyList()

    private fun load(): String = script.scriptLoad(source).also { sha = it }
}

/**
 * Writes messages of one shard.
 *
 * `KEYS`: the messages, order keys, all, and pending keys of the shard.
 * `ARGV`: the shard tag, the number of messages, and then, for each message, its UUID,
 * encoded order key, `1` if it is `TO_DELIVER` or `0` otherwise, and bytes.
 *
 * Replaces the stored message with the same UUID, removing its old members from
 * the sorted sets. Publishes the shard tag.
 */
internal const val WRITE_SCRIPT = """
local count = tonumber(ARGV[2])
for i = 0, count - 1 do
    local at = 3 + i * 4
    local uuid = ARGV[at]
    local key = ARGV[at + 1]
    local old = redis.call('HGET', KEYS[2], uuid)
    if old then
        local oldMember = old .. ':' .. uuid
        redis.call('ZREM', KEYS[3], oldMember)
        redis.call('ZREM', KEYS[4], oldMember)
    end
    redis.call('HSET', KEYS[1], uuid, ARGV[at + 3])
    redis.call('HSET', KEYS[2], uuid, key)
    local member = key .. ':' .. uuid
    redis.call('ZADD', KEYS[3], 0, member)
    if ARGV[at + 2] == '1' then
        redis.call('ZADD', KEYS[4], 0, member)
    end
end
redis.call('PUBLISH', 'delivery:changes:inbox', ARGV[1])
return {count}
"""

/**
 * Deletes messages of one shard.
 *
 * `KEYS`: the messages, order keys, all, and pending keys of the shard.
 * `ARGV`: the shard tag, and then the UUIDs.
 *
 * Publishes the shard tag if anything was removed.
 */
internal const val DELETE_SCRIPT = """
local removed = 0
for i = 2, #ARGV do
    local uuid = ARGV[i]
    local key = redis.call('HGET', KEYS[2], uuid)
    if key then
        local member = key .. ':' .. uuid
        redis.call('ZREM', KEYS[3], member)
        redis.call('ZREM', KEYS[4], member)
        redis.call('HDEL', KEYS[1], uuid)
        redis.call('HDEL', KEYS[2], uuid)
        removed = removed + 1
    end
end
if removed > 0 then
    redis.call('PUBLISH', 'delivery:changes:inbox', ARGV[1])
end
return {removed}
"""

/**
 * Reads a page of one shard.
 *
 * `KEYS`: the messages and all keys of the shard.
 * `ARGV`: the lower bound of `ZRANGEBYLEX`, and the page size.
 *
 * Returns the number of members without a value, which it skips, followed by the bytes
 * of the messages.
 */
internal const val PAGE_SCRIPT = """
local members = redis.call('ZRANGEBYLEX', KEYS[2], ARGV[1], '+', 'LIMIT', 0, tonumber(ARGV[2]))
local result = {0}
for i = 1, #members do
    local value = redis.call('HGET', KEYS[1], string.sub(members[i], 44))
    if value then
        result[#result + 1] = value
    else
        result[1] = result[1] + 1
    end
end
return result
"""

/**
 * Finds the `TO_DELIVER` message of one shard with the largest order key.
 *
 * `KEYS`: the messages and pending keys of the shard.
 *
 * Returns the number of members without a value, which it skips, followed by the bytes
 * of the message, if there is one.
 */
internal const val NEWEST_SCRIPT = """
local missing = 0
while true do
    local members = redis.call('ZREVRANGEBYLEX', KEYS[2], '+', '-', 'LIMIT', missing, 1)
    if #members == 0 then
        return {missing}
    end
    local value = redis.call('HGET', KEYS[1], string.sub(members[1], 44))
    if value then
        return {missing, value}
    end
    missing = missing + 1
end
"""

/**
 * Writes a session record if the stored one is the expected one.
 *
 * `KEYS`: the sessions hash.
 * `ARGV`: the shard tag, `1` if a record is expected or `0` otherwise, the expected bytes
 * (empty if none is expected), and the bytes of the replacement.
 *
 * Returns `{1}` and publishes the shard tag if the replacement was written; otherwise,
 * returns `{0}` followed by the bytes of the current record, if there is one.
 */
internal const val COMPARE_AND_SET_SCRIPT = """
local current = redis.call('HGET', KEYS[1], ARGV[1])
local matches
if ARGV[2] == '1' then
    matches = (current == ARGV[3])
else
    matches = (current == false)
end
if not matches then
    if current then
        return {0, current}
    end
    return {0}
end
redis.call('HSET', KEYS[1], ARGV[1], ARGV[4])
redis.call('PUBLISH', 'delivery:changes:sessions', ARGV[1])
return {1}
"""
