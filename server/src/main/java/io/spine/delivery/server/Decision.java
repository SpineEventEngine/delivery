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

package io.spine.delivery.server;

import io.spine.server.delivery.ShardSessionRecord;
import org.jspecify.annotations.Nullable;

/**
 * What an operation of {@link DeliveryShardRegistry} does with the current record of a shard.
 *
 * @param <T>
 *         the type of the result of the operation, which may be a nullable type
 * @param replacement
 *         the record to write, or {@code null} if nothing is to be written
 * @param result
 *         the result of the operation, once the decision is carried out
 * @param followUp
 *         what to do once the decision is carried out, such as logging what it did
 *
 *         <p>Runs only if this decision is the one carried out, so never for a decision whose
 *         write conflicted or failed.
 */
record Decision<T extends @Nullable Object>(
        @Nullable ShardSessionRecord replacement,
        T result,
        Runnable followUp) {

    /**
     * The follow-up of a decision that has nothing to do once it is carried out.
     */
    private static final Runnable NO_FOLLOW_UP = () -> {};

    /**
     * Creates the decision to write the replacement, if any, finish with the result,
     * and then run the follow-up.
     */
    Decision {
    }

    /**
     * Writes the replacement, and then finishes with the given result.
     */
    static <T extends @Nullable Object> Decision<T> write(ShardSessionRecord replacement,
                                                          T result) {
        return write(replacement, result, NO_FOLLOW_UP);
    }

    /**
     * Writes the replacement, and then finishes with the given result and runs the follow-up.
     */
    static <T extends @Nullable Object> Decision<T> write(ShardSessionRecord replacement,
                                                          T result,
                                                          Runnable followUp) {
        return new Decision<>(replacement, result, followUp);
    }

    /**
     * Finishes with the given result, without a write.
     */
    static <T extends @Nullable Object> Decision<T> done(T result) {
        return new Decision<>(null, result, NO_FOLLOW_UP);
    }
}
