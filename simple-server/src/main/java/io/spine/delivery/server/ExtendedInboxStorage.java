/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.server;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Timestamp;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.InboxMessageId;
import io.spine.server.delivery.InboxStorage;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.storage.StorageFactory;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Extends the {@link InboxStorage} by exposing some of the API endpoints into {@code public}.
 */
public final class ExtendedInboxStorage extends InboxStorage {

    /**
     * Creates a new {@code ExtendedInboxStorage} backed by the configured {@code factory}.
     *
     * <p>The {@code multitenant} parameter determines whether the storage operates in
     * a multitenant environment.
     */
    public ExtendedInboxStorage(StorageFactory factory, boolean multitenant) {
        super(checkNotNull(factory), multitenant);
    }

    @Override
    public synchronized void writeBatch(Iterable<InboxMessage> messages) {
        super.writeBatch(messages);
    }

    @CanIgnoreReturnValue
    @Override
    public synchronized boolean delete(InboxMessageId id) {
        return super.delete(id);
    }

    @Override
    public synchronized void deleteAll(Iterable<InboxMessageId> ids) {
        super.deleteAll(ids);
    }

    @Override
    public synchronized Optional<InboxMessage> read(InboxMessageId id) {
        return super.read(id);
    }

    @Override
    public synchronized ImmutableList<InboxMessage>
    readAll(ShardIndex index, @Nullable Timestamp sinceWhen, int pageSize) {
        return super.readAll(index, sinceWhen, pageSize);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Exposing this method for maintenance purposes.
     */
    @Override
    public Iterator<InboxMessage> readAll() {
        return super.readAll();
    }
}
