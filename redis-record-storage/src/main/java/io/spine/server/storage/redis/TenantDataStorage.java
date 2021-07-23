/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
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
