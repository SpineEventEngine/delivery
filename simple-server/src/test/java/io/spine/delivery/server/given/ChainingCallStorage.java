/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.server.given;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.delivery.server.event.TestEvent;
import io.spine.delivery.server.event.TestEventId;
import io.spine.query.RecordQuery;
import io.spine.server.storage.RecordStorage;
import io.spine.server.storage.RecordWithColumns;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import static io.spine.delivery.server.given.StoragesTestEnv.newTestContext;
import static io.spine.delivery.server.given.StoragesTestEnv.specForTestEvent;

/**
 * In-memory storage that implemented in the way where all methods delegate their work to other
 * methods of this class, and only {@code writeRecord()} and {@code deleteRecord()} do the actual
 * saving to the underlying storage.
 *
 * @see DirectCallStorage
 */
@VisibleForTesting
public final class ChainingCallStorage extends RecordStorage<TestEventId, TestEvent> {

    private final Map<TestEventId, TestEvent> store = new HashMap<>();

    /**
     * Creates the new storage instance.
     */
    public ChainingCallStorage() {
        super(newTestContext(), specForTestEvent());
    }

    @Override
    public Iterator<TestEventId> index(RecordQuery<TestEventId, TestEvent> query) {
        throw new UnsupportedOperationException("Index with query is not supported.");
    }

    @Override
    public Iterator<TestEvent> readAllRecords(RecordQuery<TestEventId, TestEvent> query) {
        return store.values()
                    .iterator();
    }

    @Override
    public Iterator<TestEventId> index() {
        return store.keySet()
                    .iterator();
    }

    @Override
    public void writeRecord(RecordWithColumns<TestEventId, TestEvent> record) {
        store.put(record.id(), record.record());
    }

    @Override
    public void
    writeAllRecords(Iterable<? extends RecordWithColumns<TestEventId, TestEvent>> records) {
        records.forEach(this::writeRecord);
    }

    @CanIgnoreReturnValue
    @Override
    public boolean deleteRecord(TestEventId id) {
        return Optional.ofNullable(store.remove(id))
                       .isPresent();
    }

    @Override
    public void write(TestEventId id, TestEvent record) {
        writeRecord(RecordWithColumns.of(id, record));
    }

    @Override
    public void write(RecordWithColumns<TestEventId, TestEvent> record) {
        writeRecord(record);
    }

    @Override
    public void writeAll(Iterable<? extends RecordWithColumns<TestEventId, TestEvent>> records) {
        records.forEach(this::write);
    }

    @CanIgnoreReturnValue
    @Override
    protected boolean delete(TestEventId id) {
        return deleteRecord(id);
    }

    @Override
    protected void deleteAll(Iterable<TestEventId> ids) {
        ids.forEach(this::delete);
    }
}
