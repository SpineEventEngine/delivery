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

import com.google.protobuf.Message;
import io.spine.query.RecordQuery;
import io.spine.server.ContextSpec;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.RecordStorage;
import io.spine.server.storage.RecordWithColumns;
import io.spine.server.storage.StorageGroup;
import org.jspecify.annotations.Nullable;
import org.redisson.api.RedissonClient;

import java.util.Iterator;

/**
 * A Redis-based implementation of {@link RecordStorage}.
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
     * @param group
     *         the group to which the storage belongs, or {@code null} for a storage
     *         outside any group
     * @param client
     *         the access client to the Redis instance
     */
    RedisRecordStorage(ContextSpec context,
                       RecordSpec<I, R> recordSpec,
                       @Nullable StorageGroup group,
                       RedissonClient client) {
        super(context, recordSpec);
        this.multitenantStorage =
                new FlatTenantStorage<>(context.isMultitenant(), recordSpec, group, client);
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
        var tenantRecords = records();
        for (var record : records) {
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
