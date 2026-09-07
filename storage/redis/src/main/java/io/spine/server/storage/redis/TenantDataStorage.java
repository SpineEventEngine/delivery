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

package io.spine.server.storage.redis;

import java.util.Iterator;
import java.util.Optional;

/**
 * Base interface for data stored in memory for one tenant.
 *
 * @param <I>
 *         the type of entity IDs
 * @param <R>
 *         the type of stored records
 */
interface TenantDataStorage<I, R> {

    /**
     * Returns an iterator over identifiers of stored records.
     */
    Iterator<I> index();

    /**
     * Obtains a record with the passed ID.
     */
    Optional<R> get(I id);

    /**
     * Puts the record into the storage.
     */
    void put(I id, R record);

    /**
     * Verifies whether the storage is empty.
     */
    boolean isEmpty();
}
