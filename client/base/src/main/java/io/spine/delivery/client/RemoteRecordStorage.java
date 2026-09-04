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
import io.spine.query.RecordQuery;
import io.spine.server.ContextSpec;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.InboxMessageId;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.RecordStorage;
import io.spine.server.storage.RecordWithColumns;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Streams.stream;
import static io.spine.util.Exceptions.newIllegalStateException;
import static java.util.stream.Collectors.groupingBy;

/**
 * A {@code RecordStorage} routing its operations to the remote Inbox
 * via an {@link InboxClient}.
 *
 * <p>Serves as the record storage underlying {@link RemoteInboxStorage}. The framework
 * reaches this storage for the operations that {@code InboxStorage} does not expose for
 * overriding — most notably, the removal of the delivered messages. The high-level read
 * and write operations are intercepted by {@code RemoteInboxStorage} itself and thus
 * never arrive here.
 *
 * <p>Arbitrary record queries cannot be translated into the gRPC protocol of
 * the Delivery server, so {@linkplain #readAllRecords(RecordQuery) query-based reads}
 * are not supported.
 */
final class RemoteRecordStorage extends RecordStorage<InboxMessageId, InboxMessage> {

    private final Supplier<InboxClient> client;

    RemoteRecordStorage(ContextSpec context,
                        RecordSpec<InboxMessageId, InboxMessage> spec,
                        Supplier<InboxClient> client) {
        super(context, spec);
        this.client = checkNotNull(client);
    }

    @Override
    public Optional<InboxMessage> read(InboxMessageId id) {
        checkNotNull(id);
        return client().find(id);
    }

    @Override
    @SuppressWarnings("ReturnValueIgnored" /* It's OK to just throw the exception. */)
    public void write(InboxMessageId id, InboxMessage record) {
        checkNotNull(id);
        checkNotNull(record);
        client().writeMessage(record);
    }

    @Override
    @SuppressWarnings("ReturnValueIgnored" /* It's OK to just throw the exception. */)
    protected void writeRecord(RecordWithColumns<InboxMessageId, InboxMessage> record) {
        checkNotNull(record);
        client().writeMessage(record.record());
    }

    @Override
    protected void writeAllRecords(
            Iterable<? extends RecordWithColumns<InboxMessageId, InboxMessage>> records) {
        checkNotNull(records);
        var messages = stream(records)
                .map(RecordWithColumns::record)
                .collect(toImmutableList());
        if (messages.isEmpty()) {
            return;
        }
        var shard = messages.get(0)
                            .shardIndex();
        client().writeMessages(shard, messages);
    }

    @Override
    protected Iterator<InboxMessage> readAllRecords(
            RecordQuery<InboxMessageId, InboxMessage> query) {
        throw unsupportedQuery();
    }

    /**
     * {@inheritDoc}
     *
     * <p>The removal is best-effort: the remote Inbox reports no per-record feedback,
     * so this method always returns {@code true}.
     */
    @Override
    protected boolean deleteRecord(InboxMessageId id) {
        checkNotNull(id);
        deleteAll(ImmutableList.of(id));
        return true;
    }

    @Override
    protected void deleteAll(Iterable<InboxMessageId> ids) {
        checkNotNull(ids);
        var byShard =
                stream(ids).collect(groupingBy(InboxMessageId::getIndex));
        byShard.forEach(this::removeAllInShard);
    }

    /**
     * Removes the messages with the given identifiers from the given shard.
     *
     * <p>The server removes the messages by their identifiers, so it is sufficient
     * to send skeleton messages carrying only the ID.
     */
    @SuppressWarnings("ReturnValueIgnored" /* It's OK to just throw the exception. */)
    private void removeAllInShard(ShardIndex shard, Iterable<InboxMessageId> ids) {
        var skeletons = stream(ids)
                .map(id -> InboxMessage.newBuilder()
                        .setId(id)
                        .buildPartial())
                .collect(toImmutableList());
        client().removeMessages(shard, skeletons);
    }

    @Override
    protected Iterator<InboxMessageId> index(RecordQuery<InboxMessageId, InboxMessage> query) {
        throw unsupportedQuery();
    }

    @Override
    public Iterator<InboxMessageId> index() {
        throw newIllegalStateException(
                "The `index` method is called from within the Inbox Storage."
        );
    }

    private static IllegalStateException unsupportedQuery() {
        return newIllegalStateException(
                "Record queries cannot be translated into the gRPC protocol" +
                        " of the Delivery server."
        );
    }

    private InboxClient client() {
        return client.get();
    }
}
