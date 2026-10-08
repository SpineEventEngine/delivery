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
 * The name of the thread that reads and sends the updates.
 */
private const val THREAD_NAME = "delivery-shard-updates"

/**
 * How long [ShardUpdateSender.close] waits for the sending thread to stop.
 */
private const val SHUTDOWN_TIMEOUT_SECONDS = 5L

/**
 * The shortest delay before a failed read is retried.
 */
private val MIN_RETRY_DELAY: Duration = Duration.ofMillis(10)

/**
 * The longest delay before a failed read is retried, unless the interval is longer.
 */
private val MAX_RETRY_DELAY: Duration = Duration.ofSeconds(1)

/**
 * Sends [ShardInfoUpdate]s to the admin subscribers of this node.
 *
 * Follows three rules:
 *
 * 1. The stores report every shard that changes, through any node. Nothing else is
 *    carried, neither the count nor the record.
 * 2. Updates are throttled per shard. When a shard changes and it was last read more
 *    than the interval ago, the shard is due at once; otherwise, it is due when
 *    the interval ends. Further changes until then are absorbed into that update.
 *    One shard never postpones another.
 * 3. An update carries the full state of the shard, read when the update is sent. A single
 *    thread works in sweeps: it takes all the shards that are due, clears their marks,
 *    reads their records and counts in one batch, and sends each shard's state to every
 *    subscriber that has not received exactly that state last.
 *
 * Marking a shard as changed is a constant-time update of a map, so the operations
 * that change the stores never wait for the updates. A change that lands while its shard
 * is being read marks the shard again, so a later sweep sends it.
 *
 * A new subscriber gets the acknowledgment first. Then, on the sending thread, it gets
 * the current state of every known shard, and only after that does it join the sweeps.
 * The known shards are the shards with a session record, the shards with messages, and
 * the shards that a subscriber last received in another state than that of a shard with
 * neither, so that a shard whose data vanished is reported as such.
 *
 * When a store reports that changes may have been missed, every known shard is marked
 * as changed, so that a shard emptied in the meantime is reported with a count of 0.
 *
 * While there are no subscribers, changes are not even marked.
 *
 * @param inbox The store of the messages.
 * @param sessions The store of the shard sessions.
 * @param interval The shortest time between two updates of one shard; zero turns
 *   the throttling off.
 */
internal class ShardUpdateSender(
    private val inbox: InboxStore,
    private val sessions: ShardSessionStore,
    interval: Duration
) : AutoCloseable, WithLogging {

    private val intervalNanos = interval.toNanos()
    private val minRetryNanos = max(MIN_RETRY_DELAY.toNanos(), intervalNanos)
    private val maxRetryNanos = max(MAX_RETRY_DELAY.toNanos(), minRetryNanos)

    private val executor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, THREAD_NAME).apply { isDaemon = true }
        }

    private val sweepTask = Runnable { logFailures { sweep() } }

    private val shards = ConcurrentHashMap<ShardIndex, ShardState>()

    /**
     * The subscribers, from the moment they are acknowledged.
     */
    private val subscribers: MutableSet<Subscriber> = ConcurrentHashMap.newKeySet()

    /**
     * When the reads may be tried again after a failure, valid while [retryDelay] is not
     * zero; accessed on the sending thread only.
     */
    private var retryAt = 0L

    /**
     * The delay before the next retry, or zero if the last read succeeded; accessed on
     * the sending thread only.
     */
    private var retryDelay = 0L

    // The last property: it passes `this` to the stores, which may call it at once, so
    // every other property must be initialized before.
    private val storeSubscriptions = listOf(
        inbox.subscribe { changed(it) },
        sessions.subscribe { changed(it) },
        inbox.subscribeToMissedChanges { missedChanges() },
        sessions.subscribeToMissedChanges { missedChanges() }
    )

    /**
     * Adds the subscriber.
     *
     * Sends it the acknowledgment on the calling thread. Then, on the sending thread,
     * sends it the current state of every known shard, after which the subscriber
     * receives the updates of the sweeps.
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
        execute(0) { join(subscriber) }
    }

    /**
     * Stops the sending thread and releases the subscriptions to the stores.
     */
    override fun close() {
        try {
            storeSubscriptions.forEach { it.cancel() }
        } finally {
            stopSending()
        }
    }

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

    private fun changed(shard: ShardIndex) {
        if (subscribers.isEmpty()) {
            return
        }
        val state = shards.computeIfAbsent(shard) { ShardState() }
        val delay = state.mark(System.nanoTime(), intervalNanos)
        if (delay >= 0) {
            schedule(delay)
        }
    }

    private fun missedChanges() {
        if (subscribers.isEmpty()) {
            return
        }
        execute(0) { markKnown() }
    }

    /**
     * Marks every known shard as changed.
     */
    private fun markKnown() {
        val now = System.nanoTime()
        val wait = retryWait(now)
        if (wait > 0) {
            execute(wait) { markKnown() }
            return
        }
        val known = try {
            readKnown().map { it.index }
        } catch (e: RuntimeException) {
            logger.atWarning().withCause(e).log {
                "Reading the known shards after missed changes failed. They will be read again."
            }
            execute(failed(now)) { markKnown() }
            return
        }
        succeeded()
        known.forEach(::changed)
    }

    private fun schedule(delayNanos: Long) {
        try {
            executor.schedule(sweepTask, delayNanos, NANOSECONDS)
        } catch (_: RejectedExecutionException) {
            // The sender is closed.
        }
    }

    private fun execute(delayNanos: Long, action: () -> Unit) {
        try {
            executor.schedule({ logFailures(action) }, delayNanos, NANOSECONDS)
        } catch (_: RejectedExecutionException) {
            // The sender is closed.
        }
    }

    private fun sweep() {
        val now = System.nanoTime()
        val wait = retryWait(now)
        if (wait > 0) {
            schedule(wait)
            return
        }
        val due = ArrayList<ShardIndex>()
        for ((shard, state) in shards) {
            if (state.takeIfDue(now)) {
                due.add(shard)
            }
        }
        if (due.isEmpty()) {
            return
        }
        val updates = try {
            read(due)
        } catch (e: RuntimeException) {
            logger.atWarning().withCause(e).log {
                "Reading the state of ${due.size} shards failed. It will be read again."
            }
            due.forEach { shards.getValue(it).markAgain(now) }
            schedule(failed(now))
            return
        }
        succeeded()
        for (update in updates) {
            subscribers.forEach { if (it.joined) it.send(update) }
        }
    }

    private fun read(due: List<ShardIndex>): List<ShardInfoUpdate> {
        val counts = inbox.count(due)
        val records = sessions.read(due)
        return due.mapNotNull { state(it, records[it]?.record, counts[it] ?: 0) }
    }

    /**
     * Returns the update with the given state of the shard, or `null` if a defective
     * client made the state impossible to express, for example with a shard index that
     * is not set.
     *
     * Such a shard is left out of the updates, so that it does not stop the others.
     */
    private fun state(
        shard: ShardIndex,
        record: ShardSessionRecord?,
        count: Int
    ): ShardInfoUpdate? =
        try {
            currentState(shard, record, count)
        } catch (e: IllegalArgumentException) {
            logger.atWarning().withCause(e).log {
                "The state of the shard `${shard.tag()}` cannot be sent to admin subscribers."
            }
            null
        }

    private fun join(subscriber: Subscriber) {
        if (subscriber !in subscribers) {
            return
        }
        val now = System.nanoTime()
        val wait = retryWait(now)
        if (wait > 0) {
            execute(wait) { join(subscriber) }
            return
        }
        val states = try {
            readKnown()
        } catch (e: RuntimeException) {
            logger.atWarning().withCause(e).log {
                "Reading the state of the shards for a new subscriber failed." +
                        " It will be read again."
            }
            execute(failed(now)) { join(subscriber) }
            return
        }
        succeeded()
        for (state in states) {
            if (!subscriber.send(state)) {
                return
            }
        }
        subscriber.joined = true
    }

    private fun readKnown(): List<ShardInfoUpdate> {
        val records = sessions.readAll().associateBy({ it.record.index }, { it.record })
        val counts = inbox.counts()
        val known = LinkedHashSet<ShardIndex>(records.keys)
        known.addAll(counts.keys)
        subscribers.forEach { known.addAll(it.shardsWithData()) }
        return known.mapNotNull { state(it, records[it], counts[it] ?: 0) }
    }

    /**
     * Returns how long the reads must still wait after a failure, or zero if they need
     * not wait.
     */
    private fun retryWait(now: Long): Long =
        if (retryDelay == 0L) 0L else max(retryAt - now, 0L)

    /**
     * Records a failed read, and returns the delay before the reads are tried again.
     */
    private fun failed(now: Long): Long {
        retryDelay = if (retryDelay == 0L) minRetryNanos else min(retryDelay * 2, maxRetryNanos)
        retryAt = now + retryDelay
        return retryDelay
    }

    private fun succeeded() {
        retryDelay = 0L
    }

    @Suppress("TooGenericExceptionCaught") // Nothing must stop the sending thread.
    private fun logFailures(action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            logger.atError().withCause(e).log { "Sending the shard updates failed." }
        }
    }

    /**
     * An admin subscriber of this node.
     */
    private inner class Subscriber(
        private val observer: ServerCallStreamObserver<SubscriptionResponse>
    ) {

        /**
         * Whether the subscriber has received its initial state, after which the sweeps
         * serve it; accessed on the sending thread only.
         */
        var joined = false

        /**
         * The state of each shard that the subscriber received last; accessed on
         * the sending thread only.
         */
        private val lastReceived = HashMap<ShardIndex, ShardInfoUpdate>()

        /**
         * Whether sending to the subscriber failed, after which nothing is sent to it;
         * accessed on the sending thread only.
         */
        private var failed = false

        /**
         * Sends the update, unless it is the state the subscriber received last.
         *
         * A subscriber that fails to receive it is removed, and its call is closed.
         *
         * @return `false` if the subscriber has failed, now or before
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

        private fun closeWithError(e: RuntimeException) {
            try {
                observer.onError(e)
            } catch (_: RuntimeException) {
                // The call is already closed.
            }
        }

        /**
         * Returns the shards that the subscriber last received with messages, picked, or
         * with the time of a pick: in another state than that of a shard with neither
         * messages nor a session record.
         */
        fun shardsWithData(): List<ShardIndex> =
            lastReceived.values
                .filter(::hasData)
                .map { it.index }

        private fun hasData(state: ShardInfoUpdate): Boolean =
            state.newMessagesCount > 0 || state.newStatus == PICKED || state.hasWhenLastPicked()
    }
}

/**
 * The throttling state of one shard.
 */
private class ShardState {

    private var marked = false
    private var dueAt = 0L
    private var wasRead = false
    private var lastReadAt = 0L

    /**
     * Marks the shard as changed.
     *
     * @return the delay in nanoseconds until the shard is due, or -1 if the shard was
     *   already marked
     */
    @Synchronized
    fun mark(now: Long, intervalNanos: Long): Long {
        if (marked) {
            return -1
        }
        marked = true
        dueAt = if (wasRead && now - lastReadAt < intervalNanos) lastReadAt + intervalNanos else now
        return max(dueAt - now, 0)
    }

    /**
     * Marks the shard as changed and due at once, after its read has failed.
     */
    @Synchronized
    fun markAgain(now: Long) {
        marked = true
        dueAt = now
    }

    /**
     * Takes the shard for a read, if it is marked and due: clears its mark and records
     * the time of the read.
     */
    @Synchronized
    fun takeIfDue(now: Long): Boolean {
        if (!marked || dueAt - now > 0) {
            return false
        }
        marked = false
        wasRead = true
        lastReadAt = now
        return true
    }
}
