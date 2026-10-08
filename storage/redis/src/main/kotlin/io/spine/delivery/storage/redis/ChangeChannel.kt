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

import io.spine.delivery.storage.MissedChangeListeners
import io.spine.delivery.storage.Subscription
import io.spine.logging.WithLogging
import org.redisson.api.RedissonClient
import org.redisson.api.listener.StatusListener
import org.redisson.client.codec.ByteArrayCodec
import org.redisson.client.codec.StringCodec
import org.redisson.codec.CompositeCodec

/**
 * The codec of the hashes of the stores: string fields, and byte values.
 */
internal val HASH_CODEC = CompositeCodec(
    StringCodec.INSTANCE, ByteArrayCodec.INSTANCE, ByteArrayCodec.INSTANCE
)

/**
 * A subscription of a store to the channel on which the changed shard tags are published.
 *
 * Redis delivers the published messages at most once. Whenever the subscription is
 * established, for the first time or again, it therefore reports that changes may have
 * been missed.
 */
internal class ChangeChannel(
    client: RedissonClient,
    private val channel: String,
    onTag: (String) -> Unit
) : WithLogging {

    private val topic = client.getTopic(channel, StringCodec.INSTANCE)

    /**
     * The listeners of the changes this subscription may have missed.
     */
    private val missed = MissedChangeListeners()

    // Added first, so that it hears the first subscription too. Adding a listener
    // returns once the channel is subscribed.
    private val statusListenerId = topic.addListener(object : StatusListener {
        override fun onSubscribe(channel: String) {
            logger.atInfo().log { "Subscribed to the Redis channel `$channel`." }
            missed.missed()
        }

        override fun onUnsubscribe(channel: String) = Unit
    })

    private val messageListenerId = topic.addListener(String::class.java) { _, tag -> onTag(tag) }

    /**
     * Adds a listener of the changes that may have been missed.
     */
    fun subscribeToMissed(onMissed: Runnable): Subscription = missed.add(onMissed)

    /**
     * Stops listening to the channel.
     */
    fun close() {
        try {
            topic.removeListener(messageListenerId, statusListenerId)
        } finally {
            missed.clear()
        }
    }
}
