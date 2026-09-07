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
import io.spine.server.storage.query.RecordQueryMatcher;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.RecordWithColumns;
import io.spine.string.Stringifiers;
import org.jspecify.annotations.Nullable;
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
import static io.spine.server.storage.query.InMemoryRecordComparator.accordingTo;
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
    private final RecordSpec<I, R> spec;

    /**
     * Creates a new tenant records facade backed by the supplied {@code records}.
     */
    TenantRecords(RMap<String, byte[]> records, RecordSpec<I, R> spec) {
        this.records = checkNotNull(records);
        this.spec = checkNotNull(spec);
    }

    @Override
    public Iterator<I> index() {
        var keys = records.keySet();
        var result = transform(
                keys.iterator(), this::fromStorageKey
        );
        return result;
    }

    /**
     * Obtains the iterator over the identifiers of the records that match the passed query.
     */
    Iterator<I> index(RecordQuery<I, R> query) {
        var subset = findRecords(query);
        var result = transform(subset.iterator(), RecordWithColumns::id);
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
        var recordType = spec.recordType();
        try {
            @SuppressWarnings("unchecked" /* Checked by generic. */)
            var record = (R) Messages
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

    private RecordWithColumns<I, R> recordWithColumns(I id, R record, RecordSpec<I, R> spec) {
        // The current Spine SPI unifies entity- and message-record specs into a single
        // `RecordSpec<I, R>`; column extraction is driven by the spec itself.
        return RecordWithColumns.create(id, record, spec);
    }

    /**
     * Returns the message with the passed identifier and applies the given field mask to it.
     *
     * <p>If there is no such message stored, returns {@code Optional.empty()}.
     */
    Optional<R> get(I id, FieldMask mask) {
        return get(id).map(r -> new FieldMaskApplier(mask).apply(r.record()));
    }

    @Override
    public Optional<RecordWithColumns<I, R>> get(I id) {
        var recordBytes = records.get(toStorageKey(id));
        if (recordBytes == null) {
            return Optional.empty();
        }
        var record = deserialize(id, recordBytes);
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
     * Reads all the records according to the supplied {@code query}.
     *
     * <p>Filters and sorts the results based on the query spec.
     */
    Iterator<R> readAll(RecordQuery<I, R> query) {
        var fieldMask = query.mask();
        var records = findRecords(query);
        return records
                .stream()
                .map(RecordWithColumns::record)
                .map(new FieldMaskApplier(fieldMask))
                .iterator();
    }

    private List<RecordWithColumns<I, R>> findRecords(RecordQuery<I, R> query) {
        var stream = filterRecords(query.subject());
        return sortAndLimit(stream, query).collect(toList());
    }

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
        return Stringifiers.toString(key);
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
            if (input instanceof EntityRecord entityRecord) {
                return (R) maskEntityRecord(entityRecord);
            }
            return applyMask(fieldMask, input);
        }

        private EntityRecord maskEntityRecord(EntityRecord input) {
            checkNotNull(input);
            var maskedState = maskAny(input.getState());
            var result = EntityRecord
                    .newBuilder(input)
                    .setState(maskedState)
                    .build();
            return result;
        }

        private Any maskAny(Any message) {
            var stateMessage = unpack(message);
            var maskedMessage = applyMask(fieldMask, stateMessage);
            var result = pack(maskedMessage);
            return result;
        }
    }
}
