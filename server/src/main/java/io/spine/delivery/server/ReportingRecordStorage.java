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

package io.spine.delivery.server;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Message;
import io.spine.logging.WithLogging;
import io.spine.server.ContextSpec;
import io.spine.server.storage.DelegatingRecordStorage;
import io.spine.server.storage.RecordStorage;
import io.spine.server.storage.RecordWithColumns;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.UUID.randomUUID;

/**
 * Storage that can report an update operation performed with its records to subscribers.
 *
 * @param <I>
 *         the type of the record identifiers
 * @param <R>
 *         the type of the message records
 */
public final class ReportingRecordStorage<I, R extends Message>
        extends DelegatingRecordStorage<I, R>
        implements WithLogging {

    /**
     * Stores subscriptions of this storage.
     *
     * <p>The implementation is a {@code ConcurrentHashMap} to avoid
     * the {@code ConcurrentModificationException} in cases when we iterate over the map values
     * to notify subscribers and a new subscriber appears.
     */
    private final Map<String, StorageSubscriber<I, R>> subscriptions = new ConcurrentHashMap<>();

    /**
     * Initializes this storage with the instance to delegate the execution of operations to.
     *
     * @param context
     *         specification of Bounded Context in scope of which this storage is used
     * @param delegate
     *         storage instance to delegate all operations to
     */
    ReportingRecordStorage(ContextSpec context, RecordStorage<I, R> delegate) {
        super(context, delegate);
    }

    /**
     * Subscribes to the update operations of this storage with the given {@code subscriber}.
     */
    public StorageSubscription subscribe(StorageSubscriber<I, R> subscriber) {
        var id = randomUUID().toString();
        this.subscriptions.put(id, subscriber);
        return () -> subscriptions.remove(id);
    }

    @Override
    protected void writeRecord(RecordWithColumns<I, R> record) {
        super.writeRecord(record);
        notifyWrite(record.id(), record.record());
    }

    @Override
    protected void writeAllRecords(Iterable<? extends RecordWithColumns<I, R>> records) {
        super.writeAllRecords(records);
        records.forEach(record -> notifyWrite(record.id(), record.record()));
    }

    @Override
    public void write(I id, R record) {
        super.write(id, record);
        notifyWrite(id, record);
    }

    @Override
    protected void write(RecordWithColumns<I, R> record) {
        super.write(record);
        notifyWrite(record.id(), record.record());
    }

    @Override
    protected void writeAll(Iterable<? extends RecordWithColumns<I, R>> records) {
        super.writeAll(records);
        records.forEach(record -> notifyWrite(record.id(), record.record()));
    }

    @CanIgnoreReturnValue
    @Override
    protected boolean deleteRecord(I id) {
        if (super.deleteRecord(id)) {
            notifyDeleted(id);
            return true;
        }
        return false;
    }

    @CanIgnoreReturnValue
    @Override
    protected boolean delete(I id) {
        if (super.delete(id)) {
            notifyDeleted(id);
            return true;
        }
        return false;
    }

    @Override
    protected void deleteAll(Iterable<I> ids) {
        super.deleteAll(ids);
        ids.forEach(this::notifyDeleted);
    }

    /**
     * Notifies all the subscribers about a new write operation performed on the given
     * {@code record} with the given {@code id}.
     */
    private void notifyWrite(I id, R record) {
        subscriptions.forEach((key, value) -> value.onWrite(id, record));
    }

    /**
     * Notifies all the subscribers about a new delete operation performed on the given {@code id}.
     */
    private void notifyDeleted(I id) {
        subscriptions.forEach((key, value) -> value.onDelete(id));
    }
}
