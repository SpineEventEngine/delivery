/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Message;
import io.spine.server.ContextSpec;
import io.spine.server.storage.RecordStorage;
import io.spine.server.storage.RecordStorageDelegate;
import io.spine.server.storage.RecordWithColumns;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.UUID.randomUUID;

/**
 * Storage that can report update operation performed with its records to subscribers.
 *
 * @param <I>
 *         the type of the record identifiers
 * @param <R>
 *         the type of the message records
 */
public final class ReportingRecordStorage<I, R extends Message> extends RecordStorageDelegate<I, R> {

    /**
     * Stores subscriptions of this storage.
     *
     * <p>The implementation is {@code ConcurrentHashMap} to avoid
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
        String id = randomUUID().toString();
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
        subscriptions.values()
                     .forEach(sub -> sub.onWrite(id, record));
    }

    /**
     * Notifies all the subscribers about a new delete operation performed on the given {@code id}.
     */
    private void notifyDeleted(I id) {
        subscriptions.values()
                     .forEach(sub -> sub.onDelete(id));
    }
}
