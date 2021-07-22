/*
 * Copyright 2021, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.server.storage.redis;

import com.google.protobuf.Message;
import io.spine.core.TenantId;
import io.spine.query.RecordQuery;
import io.spine.server.ContextSpec;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.RecordStorage;
import io.spine.server.storage.RecordWithColumns;
import org.redisson.api.RMap;
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
        this.multitenantStorage = new MultitenantStorage<>(context.isMultitenant()) {
            @Override
            TenantRecords<I, R> createSlice(TenantId tenant) {
                Class<I> id = recordSpec.idType();
                Class<? extends Message> source = recordSpec.sourceType();
                String tenantRecordMap = String.format(
                        "%s-%s-%s", tenant.getValue(), id.getName(), source.getName()
                );
                RMap<I, RecordWithColumns<I, R>> map = client.getMap(tenantRecordMap);
                return new TenantRecords<>(map);
            }
        };
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
        for (RecordWithColumns<I, R> record : records) {
            records().put(record.id(), record);
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
