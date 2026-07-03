/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.server.storage.redis;

import com.google.protobuf.Message;
import io.spine.core.TenantId;
import io.spine.server.storage.RecordSpec;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A multitenant storage with the tenant ID prepended to each stored type.
 *
 * <p>The tenant ID is prepended to every record type forming a flat Redisson {@link RMap} key
 * of the following structure:
 *
 * <pre>{@code
 *     <Tenant ID>-<Record ID type>-<Record value type>
 * }</pre>
 *
 * @param <I>
 *         the type of the record identifiers
 * @param <R>
 *         the type of the stored records
 */
final class FlatTenantStorage<I, R extends Message> extends MultitenantStorage<TenantRecords<I, R>> {

    private final RecordSpec<I, R> recordSpec;
    private final RedissonClient client;

    /**
     * Creates a new tenant storage.
     *
     * @param multitenant
     *         determines if the storage supports multitenancy
     * @param recordSpec
     *         the type of the records stored
     * @param client
     *         the Redis access client
     */
    FlatTenantStorage(boolean multitenant, RecordSpec<I, R> recordSpec, RedissonClient client) {
        super(multitenant);
        this.client = checkNotNull(client);
        this.recordSpec = checkNotNull(recordSpec);
    }

    @Override
    TenantRecords<I, R> createSlice(TenantId tenant) {
        String tenantRecords = tenantRecords(tenant);
        return new TenantRecords<>(client.getMap(tenantRecords), recordSpec);
    }

    private String tenantRecords(TenantId tenant) {
        var idType = recordSpec.idType();
        var sourceType = recordSpec.sourceType();
        return String.format(
                "%s-%s-%s", tenant.getValue(), idType.getName(), sourceType.getName()
        );
    }
}
