/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.server.storage.hazelcast;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Message;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import io.spine.core.TenantId;
import io.spine.query.RecordQuery;
import io.spine.server.ContextSpec;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.RecordStorage;
import io.spine.server.storage.RecordWithColumns;
import io.spine.server.storage.StorageGroup;
import io.spine.server.storage.query.RecordQueryMatcher;
import io.spine.server.tenant.TenantFunction;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.stream.Stream;

import static io.spine.server.storage.query.InMemoryRecordComparator.accordingTo;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Hazelcast-based record storage.
 *
 * <p>This storage created for an experimental usage and doesn't provide strong consistency guaranty
 * in a cluster. To take further steps to provide consistency guaranty we have to perform some
 * testing in an environment closer to real.
 *
 * <p>For more info about Hazelcast,
 * please refer to its <a href="https://docs.hazelcast.com/home/">documentation</a>.
 *
 * @param <I>
 *         the type of the record identifiers
 * @param <R>
 *         the type of the stored message records
 */
public final class HazelcastRecordStorage<I, R extends Message> extends RecordStorage<I, R> {

    /**
     * Separates the group name from the record type name in the name of a map serving
     * a {@linkplain StorageGroup grouped} storage.
     *
     * <p>The separator is deliberately a character that may not occur in a Protobuf type
     * name, so that grouped map names are structurally disjoint from ungrouped ones.
     */
    private static final char GROUP_SEPARATOR = '-';

    private final HazelcastInstance hazelcast;
    private final @Nullable StorageGroup group;

    /**
     * Creates the new storage instance.
     *
     * @param context
     *         specification of the Bounded Context in scope of which the storage will be used
     * @param recordSpec
     *         definitions of the columns to store along with each record
     * @param group
     *         the group to which the storage belongs, or {@code null} for a storage
     *         outside any group
     * @param hazelcast
     *         hazelcast instance that this storage will use as its backend storage
     */
    HazelcastRecordStorage(ContextSpec context,
                           RecordSpec<I, R> recordSpec,
                           @Nullable StorageGroup group,
                           HazelcastInstance hazelcast) {
        super(context, recordSpec);
        this.group = group;
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
        var storage = getStorage();
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
     * the type name of the entity ID and the name of the stored data.
     *
     * <p>The returned name is in the given format: {@code [tenantId]idTypeName:dataName}, where
     * the data name is composed as follows:
     *
     * <ul>
     *     <li>For a storage outside any group — the name of the
     *     {@linkplain RecordSpec#sourceType() source type} of the record specification.
     *     For an entity storage, this is the class of the entity state, telling apart
     *     the storages of entity types that share the record type ({@code EntityRecord}).
     *
     *     <li>For a storage belonging to a {@linkplain StorageGroup group} — the group name
     *     followed by {@code '-'} and the simple name of the record type,
     *     e.g. {@code spine.delivery.ShardSessionRegistry-Event}. The dash may not occur
     *     in a Protobuf type name, so grouped names never collide with ungrouped ones.
     * </ul>
     *
     * @param tenantId
     *         identifier of the current tenant
     * @return name of the data storage on the Hazelcast side
     */
    private String getStorageName(TenantId tenantId) {
        var idType = recordSpec().idType();
        var dataName = group == null
                       ? recordSpec().sourceType().getName()
                       : group.getName() + GROUP_SEPARATOR
                               + recordSpec().recordType().getSimpleName();
        return format("[%s]%s:%s", tenantId.getValue(), idType.getName(), dataName);
    }

    /**
     * Returns a {@code Stream} of records that comply with the given {@code query}.
     */
    private Stream<RecordWithColumns<I, R>> queryRecords(RecordQuery<I, R> query) {
        var matcher = new RecordQueryMatcher<I, R>(query.subject());
        var stream = getStorage()
                .entrySet()
                .stream()
                .map(entry -> recordWithColumns(entry.getKey(), entry.getValue()))
                .filter(matcher);
        return sortAndLimit(stream, query);
    }

    private RecordWithColumns<I, R> recordWithColumns(I id, R record) {
        var spec = recordSpec();
        return RecordWithColumns.create(id, record, spec);
    }

    /**
     * Applies the sorting and limit settings from the given {@code query}
     * to the given {@code data}.
     */
    private static <I, R extends Message> Stream<RecordWithColumns<I, R>>
    sortAndLimit(Stream<RecordWithColumns<I, R>> data, RecordQuery<I, R> query) {
        var stream = data;
        var sortingSpecs = query.sorting();
        if (sortingSpecs.size() > 0) {
            stream = stream.sorted(accordingTo(sortingSpecs));
        }
        var limit = query.limit();
        if (limit != null && limit > 0) {
            stream = stream.limit(limit);
        }
        return stream;
    }
}
