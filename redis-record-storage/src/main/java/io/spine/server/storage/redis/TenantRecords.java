/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.server.storage.redis;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Any;
import com.google.protobuf.FieldMask;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import io.spine.protobuf.Messages;
import io.spine.query.RecordQuery;
import io.spine.query.SortBy;
import io.spine.query.Subject;
import io.spine.server.entity.EntityRecord;
import io.spine.server.entity.storage.EntityRecordSpec;
import io.spine.server.entity.storage.EntityRecordWithColumns;
import io.spine.server.storage.MessageRecordSpec;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.RecordWithColumns;
import io.spine.string.Stringifiers;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.redisson.api.RMap;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterators.transform;
import static io.spine.protobuf.AnyPacker.pack;
import static io.spine.protobuf.AnyPacker.unpack;
import static io.spine.server.entity.FieldMasks.applyMask;
import static io.spine.server.storage.redis.RecordComparator.accordingTo;
import static io.spine.util.Exceptions.newIllegalStateException;
import static java.util.stream.Collectors.toList;

/**
 * The Redis-based storage for message records.
 *
 * <p>Acts like a facade API for the operations available over the data of a single tenant.
 *
 * @param <I>
 *         the type of the record identifiers
 * @param <R>
 *         the type of the stored records
 */
final class TenantRecords<I, R extends Message>
        implements TenantDataStorage<I, RecordWithColumns<I, R>> {

    private final RMap<String, byte[]> records;
    private final RecordSpec<I, R, ?> spec;

    /**
     * Creates a new tenant records facade backed by the supplied {@code records}.
     */
    TenantRecords(RMap<String, byte[]> records, RecordSpec<I, R, ?> spec) {
        this.records = checkNotNull(records);
        this.spec = checkNotNull(spec);
    }

    @Override
    public Iterator<I> index() {
        Set<String> keys = records.keySet();
        Iterator<I> result = transform(
                keys.iterator(), this::fromStorageKey
        );
        return result;
    }

    /**
     * Obtains the iterator over the identifiers of the records which match the passed query.
     */
    Iterator<I> index(RecordQuery<I, R> query) {
        List<RecordWithColumns<I, R>> subset = findRecords(query);
        Iterator<I> result = transform(subset.iterator(), RecordWithColumns::id);
        return result;
    }

    @Override
    public void put(I id, RecordWithColumns<I, R> record) {
        records.put(toStorageKey(id), serialize(record));
    }

    private byte[] serialize(RecordWithColumns<I, R> recordWithColumns) {
        return recordWithColumns
                .record()
                .toByteArray();
    }

    @SuppressWarnings("unchecked" /* Ensured by generics and serialization approach. */)
    private RecordWithColumns<I, R> deserialize(I id, byte[] recordBytes) {
        Class<R> recordType = spec.storedType();
        try {
            @SuppressWarnings("unchecked" /* Checked by generic. */)
            R record = (R) Messages
                    .builderFor(recordType)
                    .mergeFrom(recordBytes)
                    .buildPartial();
            return recordWithColumns(id, record, spec);
        } catch (InvalidProtocolBufferException e) {
            throw newIllegalStateException(
                    e,
                    "Unable to deserialize record of type `%s` with ID `%s`.",
                    recordType, toStorageKey(id)
            );
        }
    }

    @SuppressWarnings({
            "unchecked", /* Ensured by generics and serialization approach. */
            "ChainOfInstanceofChecks" /* There is no better way to abstract this part yet. */
    })
    private RecordWithColumns<I, R> recordWithColumns(I id, R record, RecordSpec<I, R, ?> spec) {
        if (spec instanceof EntityRecordSpec) {
            return (RecordWithColumns<I, R>)
                    EntityRecordWithColumns.create(id, (EntityRecord) record);
        }
        if (spec instanceof MessageRecordSpec) {
            return RecordWithColumns.create(id, record, (RecordSpec<I, R, R>) spec);
        }
        throw newIllegalStateException("Unsupported record spec: %s", spec);
    }

    /**
     * Returns the message with the passed identifier and applies the given field mask to it.
     *
     * <p>If there is no such a message stored, returns {@code Optional.empty()}.
     */
    Optional<R> get(I id, FieldMask mask) {
        return get(id).map(r -> new FieldMaskApplier(mask).apply(r.record()));
    }

    @Override
    public Optional<RecordWithColumns<I, R>> get(I id) {
        byte[] recordBytes = records.get(toStorageKey(id));
        if (recordBytes == null) {
            return Optional.empty();
        }
        RecordWithColumns<I, R> record = deserialize(id, recordBytes);
        return Optional.of(record);
    }

    /**
     * Deletes an item with the specified {@code id}.
     *
     * @return {@code true} if the record is deleted, {@code false} otherwise
     */
    boolean delete(I id) {
        return records.fastRemove(toStorageKey(id)) > 0;
    }

    /**
     * Reads all the records according the to supplied {@code query}.
     *
     * <p>Filters and sorts the results based on the query spec.
     */
    Iterator<R> readAll(RecordQuery<I, R> query) {
        FieldMask fieldMask = query.mask();
        List<RecordWithColumns<I, R>> records = findRecords(query);
        return records
                .stream()
                .map(RecordWithColumns::record)
                .map(new FieldMaskApplier(fieldMask))
                .iterator();
    }

    private List<RecordWithColumns<I, R>> findRecords(RecordQuery<I, R> query) {
        Stream<RecordWithColumns<I, R>> stream = filterRecords(query.subject());
        return sortAndLimit(stream, query).collect(toList());
    }

    private static <I, R extends Message> Stream<RecordWithColumns<I, R>>
    sortAndLimit(Stream<RecordWithColumns<I, R>> data, RecordQuery<I, R> query) {
        Stream<RecordWithColumns<I, R>> stream = data;
        ImmutableList<SortBy<?, R>> sortingSpecs = query.sorting();
        if (sortingSpecs.size() > 0) {
            stream = stream.sorted(accordingTo(sortingSpecs.asList()));
        }
        Integer limit = query.limit();
        if (limit != null && limit > 0) {
            stream = stream.limit(limit);
        }
        return stream;
    }

    /**
     * Filters the records returning only the ones matching the
     * {@linkplain Subject subject of the record query}.
     */
    private Stream<RecordWithColumns<I, R>> filterRecords(Subject<I, R> subject) {
        Predicate<RecordWithColumns<I, R>> matcher = new RecordQueryMatcher<>(subject);
        return records
                .entrySet()
                .stream()
                .map(entry -> deserialize(fromStorageKey(entry.getKey()), entry.getValue()))
                .filter(matcher);
    }

    @Override
    public boolean isEmpty() {
        return records.isEmpty();
    }

    private I fromStorageKey(String key) {
        return Stringifiers.fromString(key, spec.idType());
    }

    private String toStorageKey(I key) {
        return Stringifiers.toString(key, spec.idType());
    }

    /**
     * A {@link Function} transforming the {@link EntityRecord} state by applying the given
     * {@link FieldMask} to it.
     *
     * <p>The resulting {@link EntityRecord} has the same fields as the given one except
     * the {@code state} field, which is masked.
     */
    private class FieldMaskApplier implements Function<R, R> {

        private final FieldMask fieldMask;

        private FieldMaskApplier(FieldMask fieldMask) {
            this.fieldMask = fieldMask;
        }

        @SuppressWarnings("unchecked")
        @Override
        public @Nullable R apply(@Nullable R input) {
            if (null == input || fieldMask.getPathsList()
                                          .isEmpty()) {
                return input;
            }
            if (input instanceof EntityRecord) {
                return (R) maskEntityRecord((EntityRecord) input);
            }
            return applyMask(fieldMask, input);
        }

        private EntityRecord maskEntityRecord(EntityRecord input) {
            checkNotNull(input);
            Any maskedState = maskAny(input.getState());
            EntityRecord result = EntityRecord
                    .newBuilder(input)
                    .setState(maskedState)
                    .vBuild();
            return result;
        }

        private Any maskAny(Any message) {
            Message stateMessage = unpack(message);
            Message maskedMessage = applyMask(fieldMask, stateMessage);
            Any result = pack(maskedMessage);
            return result;
        }
    }
}
