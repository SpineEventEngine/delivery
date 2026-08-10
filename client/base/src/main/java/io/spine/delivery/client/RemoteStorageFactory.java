/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.client;

import com.google.protobuf.Message;
import io.spine.server.ContextSpec;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.InboxMessageId;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.RecordStorage;
import io.spine.server.storage.StorageFactory;
import io.spine.server.storage.StorageGroup;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.util.Suppliers2.memoize;

/**
 * A storage factory serving {@link RemoteInboxStorage} with its underlying
 * {@linkplain RemoteRecordStorage gRPC-backed record storage}.
 *
 * <p>The factory is not meant for a general use: the only storage it ever creates
 * stores {@link InboxMessage}s remotely via an {@link InboxClient}.
 */
final class RemoteStorageFactory implements StorageFactory {

    private final Supplier<InboxClient> client;
    private boolean open = true;

    /**
     * Creates a new factory over the given client supplier.
     *
     * <p>The supplier is lazily evaluated and memoized.
     */
    RemoteStorageFactory(Supplier<InboxClient> clientSupplier) {
        checkNotNull(clientSupplier);
        this.client = memoize(clientSupplier);
    }

    /**
     * Returns the memoized client supplier shared with the created storage.
     */
    Supplier<InboxClient> client() {
        return client;
    }

    @Override
    @SuppressWarnings("unchecked") // The factory only ever stores `InboxMessage`s.
    public <I, R extends Message> RecordStorage<I, R>
    createRecordStorage(ContextSpec context,
                        RecordSpec<I, R> spec,
                        @Nullable StorageGroup group) {
        var messageSpec = (RecordSpec<InboxMessageId, InboxMessage>) spec;
        var storage = new RemoteRecordStorage(context, messageSpec, client);
        return (RecordStorage<I, R>) storage;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void close() {
        open = false;
    }
}
