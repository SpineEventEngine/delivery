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
 * Encodes the hashes of the stores in Redis: their fields as UTF-8 strings, and their values
 * as raw bytes.
 */
internal val HASH_CODEC = CompositeCodec(
    StringCodec.INSTANCE, ByteArrayCodec.INSTANCE, ByteArrayCodec.INSTANCE
)

/**
 * Listens to the Redis channel on which a store publishes the [tag][io.spine.delivery.storage.tag]
 * of every changed shard.
 *
 * Redis delivers a published message only to the subscribers connected at that moment,
 * at most once. When the connection is lost and established again, the messages published
 * in between never arrive. So each time the subscription is established, for the first
 * time or again, the channel reports that changes may have been missed.
 *
 * @param client The client connected to Redis.
 * @param channel The name of the channel.
 * @param onTag Receives the tag of every changed shard, on a thread of the client.
 */
internal class ChangeChannel(
    client: RedissonClient,
    private val channel: String,
    onTag: (String) -> Unit
) : WithLogging {

    /**
     * The channel, with its messages decoded as UTF-8 strings.
     */
    private val topic = client.getTopic(channel, StringCodec.INSTANCE)

    /**
     * The listeners of the changes this subscription may have missed.
     */
    private val missed = MissedChangeListeners()

    /**
     * The ID of the listener of the subscription, which reports missed changes whenever
     * the subscription is established.
     *
     * Added before [messageListenerId], so that it hears the first subscription too:
     * adding the first listener subscribes to the channel, and returns once subscribed.
     */
    private val statusListenerId = topic.addListener(object : StatusListener {

        /**
         * Logs the subscription, and reports that changes may have been missed.
         */
        override fun onSubscribe(channel: String) {
            logger.atInfo().log { "Subscribed to the Redis channel `$channel`." }
            missed.missed()
        }

        /**
         * Does nothing, as the channel is subscribed to again, if the connection allows.
         */
        override fun onUnsubscribe(channel: String) = Unit
    })

    /**
     * The ID of the listener of the messages, which passes each tag to `onTag`.
     */
    private val messageListenerId = topic.addListener(String::class.java) { _, tag -> onTag(tag) }

    /**
     * Adds a listener of the changes that may have been missed.
     *
     * @return The subscription that removes the listener.
     */
    fun subscribeToMissed(onMissed: Runnable): Subscription = missed.add(onMissed)

    /**
     * Stops listening to the channel, and removes the listeners of missed changes.
     */
    fun close() {
        try {
            topic.removeListener(messageListenerId, statusListenerId)
        } finally {
            missed.clear()
        }
    }
}
