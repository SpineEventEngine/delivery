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

package io.spine.delivery.client;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Timestamp;
import io.spine.logging.WithLogging;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.InboxMessageId;
import io.spine.server.delivery.InboxStorage;
import io.spine.server.delivery.Page;
import io.spine.server.delivery.ShardIndex;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.getFirst;
import static io.spine.protobuf.Messages.isDefault;
import static io.spine.util.Preconditions2.checkNotDefaultArg;
import static java.util.Objects.requireNonNull;

/**
 * An {@code InboxStorage} based on a remotely stored Inbox state.
 *
 * <p>The read and write operations invoked by the framework are overridden to run
 * through the {@linkplain InboxClient gRPC client} of the Delivery server. The removal
 * operations, which {@code InboxStorage} routes through its underlying record storage,
 * are served by a gRPC-backed record storage created by the package-private
 * {@code RemoteStorageFactory}.
 */
@SuppressWarnings({
        "UnsynchronizedOverridesSynchronized",
        "NonSynchronizedMethodOverridesSynchronizedMethod" /* Delegating to gRPC client. */
})
public final class RemoteInboxStorage extends InboxStorage implements WithLogging {

    private final Supplier<InboxClient> client;

    /**
     * Creates a new storage instance with the configured {@code clientSupplier}.
     *
     * <p>The supplier is lazily evaluated and memoized.
     */
    public RemoteInboxStorage(Supplier<InboxClient> clientSupplier) {
        this(new RemoteStorageFactory(clientSupplier));
    }

    private RemoteInboxStorage(RemoteStorageFactory factory) {
        super(factory, false);
        this.client = factory.client();
    }

    @Override
    public Page<InboxMessage> readAll(ShardIndex index, int pageSize) {
        checkNotDefaultArg(index);
        checkArgument(pageSize > 0);
        return client().readAll(index, pageSize);
    }

    @Override
    public ImmutableList<InboxMessage>
    readAll(ShardIndex index, @Nullable Timestamp sinceWhen, int pageSize) {
        checkNotDefaultArg(index);
        checkArgument(pageSize > 0);
        return client().readAll(index, sinceWhen, pageSize);
    }

    @Override
    public Optional<InboxMessage> newestMessageToDeliver(ShardIndex index) {
        checkNotDefaultArg(index);
        return client().newestMessageToDeliver(index);
    }

    @Override
    @SuppressWarnings("ReturnValueIgnored")
    protected void write(InboxMessage message) {
        checkNotDefaultArg(message);
        client().writeMessage(message);
    }

    @Override
    public void write(InboxMessageId id, InboxMessage record) {
        checkNotDefaultArg(id);
        checkNotDefaultArg(record);
        write(record);
    }

    @Override
    @SuppressWarnings("ReturnValueIgnored" /* It's OK to just throw the exception. */)
    protected void writeBatch(Iterable<InboxMessage> messages) {
        checkNotNull(messages);
        var message = requireNonNull(
                getFirst(messages, InboxMessage.getDefaultInstance())
        );
        if (isDefault(message)) {
            logger().atTrace().log(() -> "No messages supplied. Skip writing to the inbox.");
            return;
        }
        var shard = message.shardIndex();
        client().writeMessages(shard, messages);
    }

    private InboxClient client() {
        return client.get();
    }
}
