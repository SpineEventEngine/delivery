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
 * A Lua script that Redis runs as one atomic step.
 *
 * Once a script is loaded, Redis runs it by the SHA-1 digest of its source (`EVALSHA`), so
 * the source is not sent each time. When Redis no longer has the script, for example after
 * a restart, the script is loaded again.
 *
 * The arguments and the results are bytes. A script receives every key it uses in its `KEYS`
 * array, as Redis requires.
 *
 * @param client The client connected to Redis.
 * @param source The Lua source of the script.
 */
internal class LuaScript(client: RedissonClient, private val source: String) {

    /**
     * Runs scripts with their arguments and results as raw bytes.
     */
    private val script = client.getScript(ByteArrayCodec.INSTANCE)

    /**
     * The digest of the loaded script, or `null` before it is loaded.
     */
    @Volatile
    private var sha: String? = null

    /**
     * Runs the script, and returns the elements of the array it returns: byte arrays for
     * strings, and `Long`s for integers.
     *
     * @param mode Tells whether the script only reads, or also writes.
     * @param keys The keys that the script uses.
     * @param args The arguments of the script.
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

    /**
     * Runs the script by its digest.
     */
    private fun evalSha(
        mode: RScript.Mode,
        digest: String,
        keys: List<String>,
        values: Array<Any>
    ): List<Any?> =
        script.evalSha<List<Any?>>(mode, digest, RScript.ReturnType.LIST, keys, *values)
            ?: emptyList()

    /**
     * Loads the script into Redis, and remembers its digest.
     */
    private fun load(): String = script.scriptLoad(source).also { sha = it }
}

/**
 * The number of the arguments of [WRITE_SCRIPT] before those of the messages.
 */
internal const val WRITE_HEADER_ARGS = 2

/**
 * The number of the arguments of [WRITE_SCRIPT] per message.
 */
internal const val WRITE_ARGS_PER_MESSAGE = 4

/**
 * The script that writes messages of one shard.
 *
 * `KEYS` are the keys of the shard: of its [messages][messagesKey], of its
 * [order keys][orderKeysKey], of the sorted set of [all its messages][allKey], and of
 * the sorted set of its [messages to deliver][pendingKey].
 *
 * `ARGV` holds the tag of the shard, the number of the messages, and then, for each message:
 * its UUID, its encoded order key, `1` if it is to be delivered or `0` otherwise, and its
 * bytes.
 *
 * A message replaces the stored message with the same UUID. The elements of the stored
 * message are removed from the sorted sets first, as its order key may differ. At the end,
 * the script publishes the tag of the shard on [INBOX_CHANNEL].
 */
internal const val WRITE_SCRIPT = """
local count = tonumber(ARGV[2])
for i = 0, count - 1 do
    local at = $WRITE_HEADER_ARGS + 1 + i * $WRITE_ARGS_PER_MESSAGE
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
redis.call('PUBLISH', '$INBOX_CHANNEL', ARGV[1])
return {count}
"""

/**
 * The script that removes messages of one shard.
 *
 * `KEYS` are the keys of the shard, as for [WRITE_SCRIPT]. `ARGV` holds the tag of the shard,
 * and then the UUIDs of the messages.
 *
 * Removes each message from both hashes and both sorted sets. If anything was removed,
 * publishes the tag of the shard on [INBOX_CHANNEL].
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
    redis.call('PUBLISH', '$INBOX_CHANNEL', ARGV[1])
end
return {removed}
"""

/**
 * The script that reads a page of the messages of one shard.
 *
 * `KEYS` are the keys of the [messages][messagesKey] of the shard, and of the sorted set of
 * [all its messages][allKey].
 *
 * `ARGV` holds where the page starts, and the page size. The page starts at `-`, the start of
 * the sorted set, or at `[<encoded time>;`. That bound sorts after every element of a message
 * received at exactly that time, because `:`, which follows the time in an element, sorts
 * before `;`.
 *
 * Returns the number of the elements without a message, followed by the bytes of
 * the messages. An element without a message is skipped. It can only remain after someone
 * removed the keys of the shard by hand.
 */
internal const val PAGE_SCRIPT = """
local members = redis.call('ZRANGEBYLEX', KEYS[2], ARGV[1], '+', 'LIMIT', 0, tonumber(ARGV[2]))
local result = {0}
for i = 1, #members do
    local value = redis.call('HGET', KEYS[1], string.sub(members[i], $UUID_POSITION))
    if value then
        result[#result + 1] = value
    else
        result[1] = result[1] + 1
    end
end
return result
"""

/**
 * The script that finds the newest message to deliver in one shard: the last element of
 * the sorted set of its [messages to deliver][pendingKey].
 *
 * `KEYS` are the keys of the [messages][messagesKey] of the shard, and of the sorted set of its
 * messages to deliver.
 *
 * Returns the number of the elements without a message, which it skips, as [PAGE_SCRIPT]
 * does, followed by the bytes of the message, if there is one.
 */
internal const val NEWEST_SCRIPT = """
local missing = 0
while true do
    local members = redis.call('ZREVRANGEBYLEX', KEYS[2], '+', '-', 'LIMIT', missing, 1)
    if #members == 0 then
        return {missing}
    end
    local value = redis.call('HGET', KEYS[1], string.sub(members[1], $UUID_POSITION))
    if value then
        return {missing, value}
    end
    missing = missing + 1
end
"""

/**
 * The script that writes a session record if the stored one is the expected one.
 *
 * `KEYS` is the key of the hash of the [session records][SESSIONS_KEY].
 *
 * `ARGV` holds the tag of the shard, `1` if a record is expected or `0` otherwise,
 * the [stored form][io.spine.delivery.storage.sessionForm] of the expected record, which is
 * empty if none is expected, and the stored form of the replacement.
 *
 * If the replacement was written, publishes the tag of the shard on [SESSIONS_CHANNEL],
 * and returns `{1}`. Otherwise, returns `{0}`, followed by the stored form of the current
 * record, if there is one.
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
redis.call('PUBLISH', '$SESSIONS_CHANNEL', ARGV[1])
return {1}
"""
