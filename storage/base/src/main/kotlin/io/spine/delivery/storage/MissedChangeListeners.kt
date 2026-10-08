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

package io.spine.delivery.storage

import io.spine.logging.WithLogging
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The listeners of the changes that a store may have missed.
 *
 * A listener that throws does not stop the others: its exception is logged.
 *
 * @see InboxStore.subscribeToMissedChanges
 * @see ShardSessionStore.subscribeToMissedChanges
 */
public class MissedChangeListeners : WithLogging {

    private val listeners = CopyOnWriteArrayList<Runnable>()

    /**
     * Adds the listener.
     *
     * @return the subscription that removes the listener
     */
    public fun add(listener: Runnable): Subscription {
        listeners.add(listener)
        return Subscription { listeners.remove(listener) }
    }

    /**
     * Tells every listener that changes may have been missed.
     */
    @Suppress("TooGenericExceptionCaught") // A listener may fail in any way.
    public fun missed() {
        for (listener in listeners) {
            try {
                listener.run()
            } catch (e: Exception) {
                logger.atError().withCause(e).log { "A listener of missed changes failed." }
            }
        }
    }

    /**
     * Removes all listeners.
     */
    public fun clear() {
        listeners.clear()
    }
}
