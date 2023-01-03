/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.server.storage.redis;

import com.google.protobuf.Message;
import io.spine.query.RecordQuery;
import io.spine.server.ContextSpec;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.RecordStorage;
import io.spine.server.storage.RecordWithColumns;
import org.redisson.api.RedissonClient;

import java.util.Iterator;

/**
 * An in-memory implementation of {@link RecordStorage}.
 *
 * @param <I>
 *         the type of the record identifiers
 * @param <R>
 *         the type of the stored records
 */
public final class RedisRecordStorage<I, R extends Message> extends RecordStorage<I, R> {

    private final MultitenantStorage<TenantRecords<I, R>> multitenantStorage;

    /**
     * Creates a new storage instance.
     *
     * @param context
     *         the specification of the context for which the storage is created
     * @param recordSpec
     *         the specification of the record stored in the storage
     * @param client
     *         the access client to the Redis instance
     */
    RedisRecordStorage(ContextSpec context, RecordSpec<I, R, ?> recordSpec, RedissonClient client) {
        super(context, recordSpec);
        this.multitenantStorage =
                new FlatTenantStorage<>(context.isMultitenant(), recordSpec, client);
    }

    private TenantRecords<I, R> records() {
        return multitenantStorage.currentSlice();
    }

    @Override
    public Iterator<I> index() {
        return records().index();
    }

    @Override
    protected Iterator<I> index(RecordQuery<I, R> query) {
        return records().index(query);
    }

    @Override
    public void write(I id, R record) {
        writeRecord(RecordWithColumns.of(id, record));
    }

    @Override
    protected void writeRecord(RecordWithColumns<I, R> record) {
        records().put(record.id(), record);
    }

    @Override
    protected void writeAllRecords(Iterable<? extends RecordWithColumns<I, R>> records) {
        TenantRecords<I, R> tenantRecords = records();
        for (RecordWithColumns<I, R> record : records) {
            tenantRecords.put(record.id(), record);
        }
    }

    @Override
    protected Iterator<R> readAllRecords(RecordQuery<I, R> query) {
        return records().readAll(query);
    }

    @Override
    protected boolean deleteRecord(I id) {
        return records().delete(id);
    }
}
