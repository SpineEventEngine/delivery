/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.server.storage.redis;

import io.spine.core.TenantId;
import io.spine.server.tenant.TenantFunction;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.Collections.synchronizedMap;
import static java.util.Objects.requireNonNull;

/**
 * The multitenant storage.
 *
 * @param <S>
 *         the type of the storage "slice" for each tenant
 */
abstract class MultitenantStorage<S extends TenantDataStorage<?, ?>> {

    /** The lock for {@code MultitenantStorage} accessor methods. */
    private final Lock lock = new ReentrantLock();

    /** The map from {@code TenantId} to its slice of data. */
    private final Map<TenantId, S> tenantSlices = synchronizedMap(new HashMap<>());

    /** If {@code true} the storage will contain a data slice for each tenant. */
    private final boolean multitenant;

    /**
     * Creates a new storage instance specifying whether it supports multitenancy.
     */
    MultitenantStorage(boolean multitenant) {
        this.multitenant = multitenant;
    }

    /**
     * Obtains the data slice for the current tenant.
     *
     * <p>If the slice has not been created for this tenant, it will be created.
     */
    final S currentSlice() {
        TenantFunction<S> func = new TenantFunction<>(isMultitenant()) {
            @Override
            public @Nullable S apply(@Nullable TenantId tenantId) {
                requireNonNull(tenantId);
                lock.lock();
                try {
                    return tenantSlices.computeIfAbsent(tenantId, id -> createSlice(id));
                } finally {
                    lock.unlock();
                }
            }
        };
        S result = func.execute();
        requireNonNull(result, "Current tenant slice is null.");
        return result;
    }

    /**
     * Creates a new tenant-specific slice of storage.
     */
    abstract S createSlice(TenantId tenant);

    /**
     * Determines if the storage is multitenant.
     */
    final boolean isMultitenant() {
        return multitenant;
    }
}
