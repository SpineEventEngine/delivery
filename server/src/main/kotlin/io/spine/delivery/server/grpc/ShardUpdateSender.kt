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

package io.spine.delivery.server.grpc

import io.grpc.stub.ServerCallStreamObserver
import io.spine.delivery.admin.ShardInfoUpdates.currentState
import io.spine.delivery.admin.SubscriptionResponses.ack
import io.spine.delivery.admin.SubscriptionResponses.toResponse
import io.spine.delivery.admin.grpc.ShardInfoUpdate
import io.spine.delivery.admin.grpc.ShardStatus.PICKED
import io.spine.delivery.admin.grpc.SubscriptionResponse
import io.spine.delivery.storage.InboxStore
import io.spine.delivery.storage.ShardSessionStore
import io.spine.delivery.storage.tag
import io.spine.logging.WithLogging
import io.spine.server.delivery.ShardIndex
import io.spine.server.delivery.ShardSessionRecord
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.math.max
import kotlin.math.min

/**
 * The name of the thread on which the updates are read and sent.
 */
private const val THREAD_NAME = "delivery-shard-updates"

/**
 * How long [ShardUpdateSender.close] waits for the sender thread to stop.
 *
 * The same time that the server waits for its calls to complete when it shuts down.
 */
private const val SHUTDOWN_TIMEOUT_SECONDS = 5L

/**
 * The delay before the first retry of a failed read, unless the throttling interval
 * is longer.
 *
 * Short, so that a failure that passes at once delays the updates only a little.
 * Each next retry waits twice as long, up to [MAX_RETRY_DELAY].
 */
private val MIN_RETRY_DELAY: Duration = Duration.ofMillis(10)

/**
 * The longest delay between two retries of a failed read, unless the throttling interval
 * is longer.
 *
 * Long enough not to load a failing store with reads, and short enough that the subscribers
 * get the current states within about a second after the store recovers.
 */
private val MAX_RETRY_DELAY: Duration = Duration.ofSeconds(1)

/**
 * Tells its subscribers how the shards change.
 *
 * A subscriber is the response stream of a gRPC call, passed to [subscribe]. It receives
 * [ShardInfoUpdate]s, each of which carries the full current state of one shard: whether
 * a worker has picked the shard, when the shard was last picked, and how many messages
 * it holds.
 *
 * ## Where the changes come from
 *
 * The [inbox] and [sessions] stores report the index of every shard that changes, whichever
 * Delivery server sharing the stores made the change. They report nothing else, neither
 * the new number of messages nor the new session record. The sender records that the shard
 * changed, and later reads the state of the shard from the stores, and sends it.
 *
 * Recording a change takes constant time, so the code that changes the stores never waits
 * for the subscribers. All the reading and sending happens on one thread, the sender thread,
 * which the sender owns.
 *
 * ## Throttling
 *
 * At most one update of a shard is sent per [interval]. When a shard changes, and its state
 * was not read for sending during the last interval, its update is sent at once. Otherwise,
 * the update is sent when the interval ends, with the state as of that moment, so that all
 * the changes made in the meantime arrive as one update. Each shard is throttled on its own:
 * a shard that changes often never delays the updates of another.
 *
 * A subscriber never receives the same state of a shard twice in a row.
 *
 * ## New subscribers
 *
 * A new subscriber first receives the acknowledgment of its subscription. Then it receives
 * the current state of every known shard, and only after that the updates of later changes.
 * A change made in between is recorded as any other, so it is not lost.
 *
 * The known shards are:
 *  - the shards that have a session record;
 *  - the shards that hold messages;
 *  - the shards that a subscriber last received with messages, as picked, or with the time
 *    of a pick. The data of such a shard may have vanished, which the subscribers must learn.
 *
 * ## Missed changes
 *
 * A store may report that it could have missed changes, for example after it connected to
 * its backend again. The sender then treats every known shard as changed.
 *
 * A shard whose index is not set, which only a defective client can write, is never sent,
 * because an update must carry the index of its shard. The other shards are sent as usual.
 *
 * While there are no subscribers, the sender ignores all changes.
 *
 * @param inbox The store of the inbox messages.
 * @param sessions The store of the shard session records.
 * @param interval The shortest time between two updates of one shard. Zero turns
 *   the throttling off.
 */
internal class ShardUpdateSender(
    private val inbox: InboxStore,
    private val sessions: ShardSessionStore,
    interval: Duration
) : AutoCloseable, WithLogging {

    /**
     * The throttling interval, in nanoseconds.
     */
    private val intervalNanos = interval.toNanos()

    /**
     * The delay before the first retry of a failed read, in nanoseconds.
     */
    private val minRetryNanos = max(MIN_RETRY_DELAY.toNanos(), intervalNanos)

    /**
     * The longest delay between two retries of a failed read, in nanoseconds.
     */
    private val maxRetryNanos = max(MAX_RETRY_DELAY.toNanos(), minRetryNanos)

    /**
     * Runs all the reading and sending, on the sender thread.
     *
     * As there is only one thread, a subscriber never receives two updates at the same
     * time, and the properties accessed on the sender thread only need no synchronization.
     */
    private val executor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, THREAD_NAME).apply { isDaemon = true }
        }

    /**
     * The task that runs [sendReadyUpdates].
     */
    private val sendReadyUpdatesTask = Runnable { logFailures { sendReadyUpdates() } }

    /**
     * The throttling of each shard that has changed while there were subscribers.
     */
    private val throttles = ConcurrentHashMap<ShardIndex, ShardThrottle>()

    /**
     * The subscribers, from the moment their subscription is acknowledged until they cancel
     * it, or fail to receive an update.
     */
    private val subscribers: MutableSet<Subscriber> = ConcurrentHashMap.newKeySet()

    /**
     * The time, by [System.nanoTime], before which a failed read is not tried again.
     *
     * Meaningful only while [retryDelay] is not zero. Accessed on the sender thread only.
     */
    private var retryAt = 0L

    /**
     * The delay before the next retry of a failed read, in nanoseconds, or zero if the last
     * read succeeded.
     *
     * Doubles after each failure, up to [maxRetryNanos]. Accessed on the sender thread only.
     */
    private var retryDelay = 0L

    /**
     * The subscriptions of the sender to the changes of the stores.
     *
     * Declared after all the other properties: subscribing passes the sender to the stores,
     * which may call it at once, so every other property must be initialized by then.
     */
    private val storeSubscriptions = listOf(
        inbox.subscribe { onChange(it) },
        sessions.subscribe { onChange(it) },
        inbox.subscribeToMissedChanges { onMissedChanges() },
        sessions.subscribeToMissedChanges { onMissedChanges() }
    )

    /**
     * Adds a subscriber.
     *
     * Sends the acknowledgment of the subscription on the calling thread. Then, on the sender
     * thread, sends the current state of every known shard, after which the subscriber
     * receives the updates of later changes.
     *
     * The subscriber is removed when its call is cancelled.
     *
     * @param observer The response stream of the subscription call.
     */
    fun subscribe(observer: ServerCallStreamObserver<SubscriptionResponse>) {
        val subscriber = Subscriber(observer)
        observer.setOnCancelHandler { subscribers.remove(subscriber) }
        subscribers.add(subscriber)
        try {
            observer.onNext(ack())
        } catch (e: RuntimeException) {
            subscribers.remove(subscriber)
            throw e
        }
        runOnSenderThread(0) { sendInitialStates(subscriber) }
    }

    /**
     * Cancels the subscriptions to the stores, and stops the sender thread.
     */
    override fun close() {
        try {
            storeSubscriptions.forEach { it.cancel() }
        } finally {
            stopSending()
        }
    }

    /**
     * Stops the sender thread, waiting up to [SHUTDOWN_TIMEOUT_SECONDS] for it, and forgets
     * the subscribers.
     */
    private fun stopSending() {
        executor.shutdownNow()
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, SECONDS)) {
                logger.atWarning().log { "The thread `$THREAD_NAME` did not stop in time." }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        subscribers.clear()
    }

    /**
     * Records that the shard changed, and schedules sending its update when the throttling
     * allows.
     *
     * The stores call it on the thread that made the change, or on a thread of their backend
     * client. Does nothing while there are no subscribers.
     */
    private fun onChange(shard: ShardIndex) {
        if (subscribers.isEmpty()) {
            return
        }
        val throttle = throttles.computeIfAbsent(shard) { ShardThrottle() }
        val delay = throttle.recordChange(System.nanoTime(), intervalNanos)
        if (delay >= 0) {
            scheduleSending(delay)
        }
    }

    /**
     * Arranges for every known shard to be treated as changed, after a store reported that
     * it could have missed changes.
     *
     * Does nothing while there are no subscribers.
     */
    private fun onMissedChanges() {
        if (subscribers.isEmpty()) {
            return
        }
        runOnSenderThread(0) { recordChangeOfKnownShards() }
    }

    /**
     * Records a change of every known shard.
     *
     * Reads the stores to learn which shards are known. If the read fails, tries again later.
     */
    private fun recordChangeOfKnownShards() {
        val now = System.nanoTime()
        val wait = retryWait(now)
        if (wait > 0) {
            runOnSenderThread(wait) { recordChangeOfKnownShards() }
            return
        }
        val known = try {
            readKnownStates().map { it.index }
        } catch (e: RuntimeException) {
            logger.atWarning().withCause(e).log {
                "Reading the known shards after missed changes failed. They will be read again."
            }
            runOnSenderThread(readFailed(now)) { recordChangeOfKnownShards() }
            return
        }
        readSucceeded()
        known.forEach(::onChange)
    }

    /**
     * Schedules a run of [sendReadyUpdates] after the given delay.
     *
     * Does nothing once the sender is closed.
     */
    private fun scheduleSending(delayNanos: Long) {
        try {
            executor.schedule(sendReadyUpdatesTask, delayNanos, NANOSECONDS)
        } catch (_: RejectedExecutionException) {
            // The sender is closed.
        }
    }

    /**
     * Runs the action on the sender thread after the given delay, and logs its failure.
     *
     * Does nothing once the sender is closed.
     */
    private fun runOnSenderThread(delayNanos: Long, action: () -> Unit) {
        try {
            executor.schedule({ logFailures(action) }, delayNanos, NANOSECONDS)
        } catch (_: RejectedExecutionException) {
            // The sender is closed.
        }
    }

    /**
     * Sends the updates of all the shards whose time to be sent has come.
     *
     * Takes these shards from their throttles, reads their states from the stores in one batch,
     * and sends each state to every subscriber that has received its initial states. If
     * the read fails, the updates become pending again, and are retried after a delay that
     * grows with each failure.
     */
    private fun sendReadyUpdates() {
        val now = System.nanoTime()
        val wait = retryWait(now)
        if (wait > 0) {
            scheduleSending(wait)
            return
        }
        val ready = ArrayList<ShardIndex>()
        for ((shard, throttle) in throttles) {
            if (throttle.takeIfReady(now)) {
                ready.add(shard)
            }
        }
        if (ready.isEmpty()) {
            return
        }
        val updates = try {
            readStates(ready)
        } catch (e: RuntimeException) {
            logger.atWarning().withCause(e).log {
                "Reading the state of ${ready.size} shards failed. It will be read again."
            }
            ready.forEach { throttles.getValue(it).retry(now) }
            scheduleSending(readFailed(now))
            return
        }
        readSucceeded()
        for (update in updates) {
            subscribers.forEach { if (it.receivesChanges) it.send(update) }
        }
    }

    /**
     * Reads the states of the given shards, in one batch per store.
     */
    private fun readStates(shards: List<ShardIndex>): List<ShardInfoUpdate> {
        val counts = inbox.count(shards)
        val records = sessions.read(shards)
        return shards.mapNotNull { stateOf(it, records[it]?.record, counts[it] ?: 0) }
    }

    /**
     * Returns the update with the given state of the shard, or `null` if a defective
     * client made the state impossible to express, for example with a shard index that
     * is not set.
     *
     * Such a shard is left out of the updates, so that it does not stop the others.
     */
    private fun stateOf(
        shard: ShardIndex,
        record: ShardSessionRecord?,
        count: Int
    ): ShardInfoUpdate? =
        try {
            currentState(shard, record, count)
        } catch (e: IllegalArgumentException) {
            logger.atWarning().withCause(e).log {
                "The state of the shard `${shard.tag()}` cannot be sent to the subscribers."
            }
            null
        }

    /**
     * Sends the current state of every known shard to a new subscriber, after which
     * the subscriber receives the updates of later changes.
     *
     * Runs on the sender thread, so no update of a change reaches the subscriber before its
     * initial states. If the read fails, tries again later.
     */
    private fun sendInitialStates(subscriber: Subscriber) {
        if (subscriber !in subscribers) {
            return
        }
        val now = System.nanoTime()
        val wait = retryWait(now)
        if (wait > 0) {
            runOnSenderThread(wait) { sendInitialStates(subscriber) }
            return
        }
        val states = try {
            readKnownStates()
        } catch (e: RuntimeException) {
            logger.atWarning().withCause(e).log {
                "Reading the state of the shards for a new subscriber failed." +
                        " It will be read again."
            }
            runOnSenderThread(readFailed(now)) { sendInitialStates(subscriber) }
            return
        }
        readSucceeded()
        for (state in states) {
            if (!subscriber.send(state)) {
                return
            }
        }
        subscriber.receivesChanges = true
    }

    /**
     * Reads the current states of all the known shards.
     */
    private fun readKnownStates(): List<ShardInfoUpdate> {
        val records = sessions.readAll().associateBy({ it.record.index }, { it.record })
        val counts = inbox.counts()
        val known = LinkedHashSet<ShardIndex>(records.keys)
        known.addAll(counts.keys)
        subscribers.forEach { known.addAll(it.shardsWithData()) }
        return known.mapNotNull { stateOf(it, records[it], counts[it] ?: 0) }
    }

    /**
     * Returns how long a read must still wait after a failure, in nanoseconds, or zero if
     * it need not wait.
     */
    private fun retryWait(now: Long): Long =
        if (retryDelay == 0L) 0L else max(retryAt - now, 0L)

    /**
     * Records a failed read, and returns the delay in nanoseconds before the next attempt.
     */
    private fun readFailed(now: Long): Long {
        retryDelay = if (retryDelay == 0L) minRetryNanos else min(retryDelay * 2, maxRetryNanos)
        retryAt = now + retryDelay
        return retryDelay
    }

    /**
     * Records a successful read, after which reads need not wait.
     */
    private fun readSucceeded() {
        retryDelay = 0L
    }

    /**
     * Runs the action, and logs its failure instead of throwing it, so that a failure never
     * stops the sender thread.
     */
    @Suppress("TooGenericExceptionCaught") // Nothing must stop the sender thread.
    private fun logFailures(action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            logger.atError().withCause(e).log { "Sending the shard updates failed." }
        }
    }

    /**
     * A subscriber to the shard updates.
     *
     * Remembers the state of each shard that it last received, so that it never receives
     * the same state twice in a row.
     *
     * @property observer The response stream of the subscription call.
     */
    private inner class Subscriber(
        private val observer: ServerCallStreamObserver<SubscriptionResponse>
    ) {

        /**
         * Whether the subscriber has received its initial states, after which it receives
         * the updates of changes.
         *
         * Accessed on the sender thread only.
         */
        var receivesChanges = false

        /**
         * The state of each shard that the subscriber received last.
         *
         * Accessed on the sender thread only.
         */
        private val lastReceived = HashMap<ShardIndex, ShardInfoUpdate>()

        /**
         * Whether sending to the subscriber has failed, after which nothing is sent to it.
         *
         * Accessed on the sender thread only.
         */
        private var failed = false

        /**
         * Sends the update, unless it is the state that the subscriber received last.
         *
         * A subscriber that fails to receive it is removed, and its call is closed.
         *
         * @return `false` if sending to the subscriber has failed, now or before.
         */
        fun send(update: ShardInfoUpdate): Boolean {
            if (failed) {
                return false
            }
            val shard = update.index
            if (lastReceived[shard] == update) {
                return true
            }
            try {
                observer.onNext(toResponse(update))
                lastReceived[shard] = update
                return true
            } catch (e: RuntimeException) {
                failed = true
                logger.atWarning().withCause(e).log {
                    "Sending the update of the shard `${shard.tag()}` failed." +
                            " The subscriber is removed."
                }
                subscribers.remove(this)
                closeWithError(e)
                return false
            }
        }

        /**
         * Closes the subscription call with the given error, unless it is already closed.
         */
        private fun closeWithError(e: RuntimeException) {
            try {
                observer.onError(e)
            } catch (_: RuntimeException) {
                // The call is already closed.
            }
        }

        /**
         * Returns the shards that the subscriber last received with messages, as picked, or
         * with the time of a pick.
         *
         * A shard without messages and without a session record has none of these, so its
         * state differs from all of them.
         */
        fun shardsWithData(): List<ShardIndex> =
            lastReceived.values
                .filter(::hasData)
                .map { it.index }

        /**
         * Tells whether the state has messages, is picked, or has the time of a pick.
         */
        private fun hasData(state: ShardInfoUpdate): Boolean =
            state.newMessagesCount > 0 || state.newStatus == PICKED || state.hasWhenLastPicked()
    }
}

/**
 * Decides when the next update of one shard may be sent.
 *
 * A change of the shard makes an update of it pending. The update may be sent at once,
 * unless the state of the shard was read for sending less than the throttling interval ago.
 * Then it may be sent when that interval ends. Changes made while the update is pending join
 * it, and make no other update.
 *
 * The methods are synchronized, because changes are recorded on any thread, while
 * the pending updates are taken on the sender thread.
 */
private class ShardThrottle {

    /**
     * Whether an update of the shard waits to be sent.
     */
    private var pending = false

    /**
     * When the pending update may be sent, by [System.nanoTime].
     */
    private var sendAt = 0L

    /**
     * Whether the state of the shard has ever been read for sending.
     */
    private var readBefore = false

    /**
     * When the state of the shard was last read for sending, by [System.nanoTime].
     */
    private var lastReadAt = 0L

    /**
     * Records a change of the shard.
     *
     * @return The delay in nanoseconds until the update may be sent, or -1 if an update was
     *   already pending, in which case its sending is already scheduled.
     */
    @Synchronized
    fun recordChange(now: Long, intervalNanos: Long): Long {
        if (pending) {
            return -1
        }
        pending = true
        sendAt = if (readBefore && now - lastReadAt < intervalNanos) {
            lastReadAt + intervalNanos
        } else {
            now
        }
        return max(sendAt - now, 0)
    }

    /**
     * Makes the update pending again, to be sent at once, after reading the state of
     * the shard has failed.
     */
    @Synchronized
    fun retry(now: Long) {
        pending = true
        sendAt = now
    }

    /**
     * Takes the pending update, if its time to be sent has come.
     *
     * A taken update is no longer pending, and the time of the read that follows is recorded.
     *
     * @return `true` if the update is taken.
     */
    @Synchronized
    fun takeIfReady(now: Long): Boolean {
        if (!pending || sendAt - now > 0) {
            return false
        }
        pending = false
        readBefore = true
        lastReadAt = now
        return true
    }
}
