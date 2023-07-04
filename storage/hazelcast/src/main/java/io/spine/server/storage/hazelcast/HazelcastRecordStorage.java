/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.server.storage.hazelcast;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Message;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import io.spine.core.TenantId;
import io.spine.query.RecordQuery;
import io.spine.query.SortBy;
import io.spine.server.ContextSpec;
import io.spine.server.entity.EntityRecord;
import io.spine.server.entity.storage.EntityRecordSpec;
import io.spine.server.entity.storage.EntityRecordWithColumns;
import io.spine.server.storage.MessageRecordSpec;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.RecordStorage;
import io.spine.server.storage.RecordWithColumns;
import io.spine.server.storage.RecordQueryMatcher;
import io.spine.server.tenant.TenantFunction;

import java.util.Iterator;
import java.util.stream.Stream;

import static io.spine.server.storage.InMemoryRecordComparator.accordingTo;
import static io.spine.util.Exceptions.newIllegalStateException;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Hazelcast-based record storage.
 *
 * @param <I> the type of the record identifiers
 * @param <R> the type of the stored message records
 */
public class HazelcastRecordStorage<I, R extends Message> extends RecordStorage<I, R> {

    private final HazelcastInstance hazelcast;

    /**
     * Creates the new storage instance.
     *
     * @param context
     *         specification of the Bounded Context in scope of which the storage will be used
     * @param recordSpec
     *         definitions of the columns to store along with each record
     */
    protected HazelcastRecordStorage(ContextSpec context,
                                     RecordSpec<I, R, ?> recordSpec,
                                     HazelcastInstance hazelcast) {
        super(context, recordSpec);
        this.hazelcast = hazelcast;
    }

    @Override
    protected Iterator<I> index(RecordQuery<I, R> query) {
        return queryRecords(query)
                .map(RecordWithColumns::id)
                .iterator();
    }

    @Override
    protected void writeRecord(RecordWithColumns<I, R> record) {
        getStorage().put(record.id(), record.record());
    }

    @Override
    protected void writeAllRecords(Iterable<? extends RecordWithColumns<I, R>> records) {
        IMap<I, R> storage = getStorage();
        records.forEach(record -> storage.put(record.id(), record.record()));
    }

    @Override
    protected Iterator<R> readAllRecords(RecordQuery<I, R> query) {
        return queryRecords(query)
                .map(RecordWithColumns::record)
                .iterator();
    }

    @CanIgnoreReturnValue
    @Override
    protected boolean deleteRecord(I id) {
        return getStorage().remove(id) != null;
    }

    @Override
    public Iterator<I> index() {
        return getStorage().keySet()
                           .iterator();
    }

    @Override
    public void write(I id, R record) {
        getStorage().put(id, record);
    }

    /**
     * Gets the storage for the current tenant and stored types.
     */
    private IMap<I, R> getStorage() {
        return requireNonNull(new TenantFunction<IMap<I, R>>(isMultitenant()) {
            @Override
            public IMap<I, R> apply(TenantId id) {
                return hazelcast.getMap(getStorageName(id));
            }
        }.execute());
    }

    /**
     * Composes the name of the storage in the specific format that includes the ID of the tenant,
     * the type name of the entity ID and the type name of the record.
     *
     * <p>The returned name is in the given format: {@code [tenantId]idTypeName:recordTypeName}.
     *
     * @param tenantId
     *         identifier of the current tenant.
     * @return name of the data storage on the Hazelcast side
     */
    private String getStorageName(TenantId tenantId) {
        Class<I> idType = recordSpec().idType();
        Class<R> storedType = recordSpec().storedType();
        return format("[%s]%s:%s", tenantId.getValue(), idType.getName(), storedType.getName());
    }

    /**
     * Returns a {@code Stream} of records that comply with the given {@code query}.
     */
    private Stream<RecordWithColumns<I, R>> queryRecords(RecordQuery<I, R> query) {
        RecordQueryMatcher<I, R> matcher = new RecordQueryMatcher<>(query.subject());
        Stream<RecordWithColumns<I, R>> stream = getStorage()
                .entrySet()
                .stream()
                .map(entry -> recordWithColumns(entry.getKey(), entry.getValue()))
                .filter(matcher);
        return sortAndLimit(stream, query);
    }

    @SuppressWarnings({
            "unchecked", /* Ensured by generics and serialization approach. */
            "ChainOfInstanceofChecks" /* There is no better way to abstract this part yet. */
    })
    private RecordWithColumns<I, R> recordWithColumns(I id, R record) {

        if (recordSpec() instanceof EntityRecordSpec) {
            return (RecordWithColumns<I, R>)
                    EntityRecordWithColumns.create(id, (EntityRecord) record);
        }
        if (recordSpec() instanceof MessageRecordSpec) {
            return RecordWithColumns.create(id, record, (RecordSpec<I, R, R>) recordSpec());
        }
        throw newIllegalStateException("Unsupported record spec: %s", recordSpec());
    }

    /**
     * Applies the sorting and limit settings from the given {@code query}
     * to the given {@code data}.
     */
    private static <I, R extends Message> Stream<RecordWithColumns<I, R>>
    sortAndLimit(Stream<RecordWithColumns<I, R>> data, RecordQuery<I, R> query) {
        Stream<RecordWithColumns<I, R>> stream = data;
        ImmutableList<SortBy<?, R>> sortingSpecs = query.sorting();
        if (sortingSpecs.size() > 0) {
            stream = stream.sorted(accordingTo(sortingSpecs));
        }
        Integer limit = query.limit();
        if (limit != null && limit > 0) {
            stream = stream.limit(limit);
        }
        return stream;
    }
}
