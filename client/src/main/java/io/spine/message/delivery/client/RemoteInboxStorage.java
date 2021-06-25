/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import io.spine.logging.Logging;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.InboxMessageId;
import io.spine.server.delivery.InboxReadRequest;
import io.spine.server.delivery.InboxStorage;
import io.spine.server.delivery.Page;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.storage.AbstractStorage;

import java.util.Iterator;
import java.util.Optional;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.util.Exceptions.newIllegalStateException;

final class RemoteInboxStorage
        extends AbstractStorage<InboxMessageId, InboxMessage, InboxReadRequest>
        implements InboxStorage, Logging {

    private final Supplier<InboxClient> client;

    RemoteInboxStorage(Supplier<InboxClient> client) {
        super(false);
        this.client = checkNotNull(client);
    }

    @Override
    public Page<InboxMessage> readAll(ShardIndex index, int pageSize) {
        throw new UnsupportedOperationException(
                "`readAll()` method is not yet implemented."
        );
    }

    @Override
    public Optional<InboxMessage> newestMessageToDeliver(ShardIndex index) {
        throw new UnsupportedOperationException(
                "`newestMessageToDeliver()` method is not yet implemented."
        );
    }

    @Override
    @SuppressWarnings("ReturnValueIgnored" /* It's OK to just throw the exception. */)
    public void write(InboxMessage message) {
        client.get()
              .writeMessage(message)
              .orElseThrow(
                      () -> newIllegalStateException("Unable to write a message to the inbox.")
              );
    }

    @Override
    public void writeAll(Iterable<InboxMessage> messages) {
        throw new UnsupportedOperationException("`writeAll()` method is not yet implemented.");
    }

    @Override
    public void removeAll(Iterable<InboxMessage> messages) {
        throw new UnsupportedOperationException("`removeAll()` method is not yet implemented.");
    }

    @Override
    public Iterator<InboxMessageId> index() {
        throw new UnsupportedOperationException("`index()` method is not yet implemented.");
    }

    @Override
    public Optional<InboxMessage> read(InboxReadRequest request) {
        throw new UnsupportedOperationException("`read()` method is not yet implemented.");
    }

    @Override
    public void write(InboxMessageId id, InboxMessage record) {
        write(record);
    }
}
