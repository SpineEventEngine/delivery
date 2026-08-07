/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.server.storage.redis;

import com.google.protobuf.Message;
import io.spine.core.TenantId;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.StorageGroup;
import org.jspecify.annotations.Nullable;
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
 *     <Tenant ID>-<Record ID type>-<Record source type>
 * }</pre>
 *
 * <p>For a storage belonging to a {@linkplain StorageGroup group} — a per-entity
 * history — the last segment is replaced by the group name followed by the simple name
 * of the record type:
 *
 * <pre>{@code
 *     <Tenant ID>-<Record ID type>-<Group name>-<Record type>
 * }</pre>
 *
 * <p>The extra {@code '-'}-separated segment may not occur in a Protobuf type name,
 * so grouped keys never collide with ungrouped ones.
 *
 * @param <I>
 *         the type of the record identifiers
 * @param <R>
 *         the type of the stored records
 */
final class FlatTenantStorage<I, R extends Message> extends MultitenantStorage<TenantRecords<I, R>> {

    /**
     * Separates the group name from the record type name in the key of a map serving
     * a {@linkplain StorageGroup grouped} storage.
     *
     * <p>The separator is deliberately a character that may not occur in a Protobuf type
     * name, so that grouped map keys are structurally disjoint from ungrouped ones.
     */
    private static final char GROUP_SEPARATOR = '-';

    private final RecordSpec<I, R> recordSpec;
    private final @Nullable StorageGroup group;
    private final RedissonClient client;

    /**
     * Creates a new tenant storage.
     *
     * @param multitenant
     *         determines if the storage supports multitenancy
     * @param recordSpec
     *         the type of the records stored
     * @param group
     *         the group to which the storage belongs, or {@code null} for a storage
     *         outside any group
     * @param client
     *         the Redis access client
     */
    FlatTenantStorage(boolean multitenant,
                      RecordSpec<I, R> recordSpec,
                      @Nullable StorageGroup group,
                      RedissonClient client) {
        super(multitenant);
        this.client = checkNotNull(client);
        this.recordSpec = checkNotNull(recordSpec);
        this.group = group;
    }

    @Override
    TenantRecords<I, R> createSlice(TenantId tenant) {
        String tenantRecords = tenantRecords(tenant);
        return new TenantRecords<>(client.getMap(tenantRecords), recordSpec);
    }

    private String tenantRecords(TenantId tenant) {
        var idType = recordSpec.idType();
        var dataName = group == null
                       ? recordSpec.sourceType().getName()
                       : group.getName() + GROUP_SEPARATOR
                               + recordSpec.recordType().getSimpleName();
        return String.format(
                "%s-%s-%s", tenant.getValue(), idType.getName(), dataName
        );
    }
}
