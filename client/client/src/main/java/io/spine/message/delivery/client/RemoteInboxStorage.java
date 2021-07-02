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
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.util.Iterator;
import java.util.Optional;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.getFirst;
import static io.spine.protobuf.Messages.isDefault;
import static io.spine.util.Exceptions.newIllegalStateException;
import static io.spine.util.Preconditions2.checkNotDefaultArg;
import static java.util.Objects.requireNonNull;

/**
 * An {@code InboxStorage} based on a remotely-stored Inbox state.
 */
public final class RemoteInboxStorage
        extends AbstractStorage<InboxMessageId, InboxMessage, InboxReadRequest>
        implements InboxStorage, Logging {

    private final Supplier<? extends InboxClient> clientSupplier;
    private @MonotonicNonNull InboxClient client;

    /**
     * Creates a new storage instance with the configured {@code clientSupplier}.
     *
     * <p>The supplier is lazily evaluated and memoized.
     */
    public RemoteInboxStorage(Supplier<? extends InboxClient> clientSupplier) {
        super(false);
        this.clientSupplier = checkNotNull(clientSupplier);
    }

    @Override
    public Page<InboxMessage> readAll(ShardIndex index, int pageSize) {
        checkNotDefaultArg(index);
        checkArgument(pageSize > 0);
        return client().readAll(index, pageSize);
    }

    @Override
    public Optional<InboxMessage> newestMessageToDeliver(ShardIndex index) {
        checkNotDefaultArg(index);
        return client().newestMessageToDeliver(index);
    }

    @Override
    @SuppressWarnings("ReturnValueIgnored" /* It's OK to just throw the exception. */)
    public void write(InboxMessage message) {
        checkNotDefaultArg(message);
        client().writeMessage(message)
                .orElseThrow(
                        () -> newIllegalStateException("Unable to write a message to the inbox.")
                );
    }

    @Override
    @SuppressWarnings("ReturnValueIgnored" /* It's OK to just throw the exception. */)
    public void writeAll(Iterable<InboxMessage> messages) {
        checkNotNull(messages);
        InboxMessage message = requireNonNull(
                getFirst(messages, InboxMessage.getDefaultInstance())
        );
        if (isDefault(message)) {
            _trace().log("No messages supplied. Skip writing to the inbox.");
            return;
        }
        ShardIndex shard = message.shardIndex();
        client().writeMessages(shard, messages)
                .orElseThrow(() -> newIllegalStateException(
                        "Unable to write messages to the inbox shard `%s`.", shard
                ));
    }

    @Override
    @SuppressWarnings("ReturnValueIgnored" /* It's OK to just throw the exception. */)
    public void removeAll(Iterable<InboxMessage> messages) {
        checkNotNull(messages);
        InboxMessage message = requireNonNull(
                getFirst(messages, InboxMessage.getDefaultInstance())
        );
        if (isDefault(message)) {
            _trace().log("No messages supplied. Skip removing messages from the inbox.");
            return;
        }
        ShardIndex shard = message.shardIndex();
        client().removeMessages(shard, messages)
                .orElseThrow(() -> newIllegalStateException(
                        "Unable to remove messages from inbox shard `%s`.", shard
                ));
    }

    @Override
    public Iterator<InboxMessageId> index() {
        throw newIllegalStateException(
                "The `index` method is called from within the Inbox Storage."
        );
    }

    @Override
    public Optional<InboxMessage> read(InboxReadRequest request) {
        checkNotNull(request);
        return client().find(request.recordId());
    }

    @Override
    public void write(InboxMessageId id, InboxMessage record) {
        checkNotDefaultArg(id);
        checkNotDefaultArg(record);
        write(record);
    }

    private InboxClient client() {
        if (client == null) {
            client = clientSupplier.get();
        }
        return client;
    }
}
