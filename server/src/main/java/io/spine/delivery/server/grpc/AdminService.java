/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package io.spine.delivery.server.grpc;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import io.spine.delivery.admin.grpc.AdminServiceGrpc;
import io.spine.delivery.admin.grpc.ShardInfo;
import io.spine.delivery.admin.grpc.ShardInfoList;
import io.spine.delivery.admin.grpc.SubscriptionResponse;
import io.spine.delivery.storage.InboxStore;
import io.spine.delivery.storage.ShardSessionStore;
import io.spine.logging.WithLogging;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.ShardSessionRecord;

import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.delivery.admin.StreamObservers.toServerCall;
import static io.spine.delivery.admin.grpc.ShardStatus.NOT_PICKED;
import static io.spine.delivery.admin.grpc.ShardStatus.PICKED;

/**
 * Allows getting information about the current state of the shards on the message delivery server.
 *
 * <p>{@code GetShardInfo} reads the stores directly. {@code SubscribeToShardUpdates} streams
 * the updates that a {@code ShardUpdateSender} throttles per shard, each carrying the full
 * current state of its shard.
 */
public final class AdminService extends AdminServiceGrpc.AdminServiceImplBase
        implements WithLogging, NamedHealthAwareService, AutoCloseable {

    /**
     * The index of a shard that is not set, which only a defective client can write.
     */
    private static final ShardIndex UNSET_INDEX = ShardIndex.getDefaultInstance();

    /**
     * Whether the service reports itself as serving.
     */
    private final AtomicBoolean healthy = new AtomicBoolean(true);

    /**
     * The store of the inbox messages, which tells the number of messages in each shard.
     */
    private final InboxStore inbox;

    /**
     * The store of the shard session records, which tells whether each shard is picked up.
     */
    private final ShardSessionStore sessions;

    /**
     * Sends the updates of the shards to the subscribers.
     */
    private final ShardUpdateSender sender;

    /**
     * Creates a new {@code AdminService} on top of the given stores.
     *
     * @param inbox
     *         the store of the messages
     * @param sessions
     *         the store of the shard sessions
     * @param updatesInterval
     *         the shortest time between two updates of one shard sent to a subscriber;
     *         zero turns the throttling off
     */
    public AdminService(InboxStore inbox, ShardSessionStore sessions, Duration updatesInterval) {
        super();
        this.inbox = checkNotNull(inbox);
        this.sessions = checkNotNull(sessions);
        checkNotNull(updatesInterval);
        this.sender = new ShardUpdateSender(inbox, sessions, updatesInterval);
    }

    @Override
    public void getShardInfo(Empty request, StreamObserver<ShardInfoList> responseObserver) {
        try {
            responseObserver.onNext(fetch());
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void
    subscribeToShardUpdates(Empty request, StreamObserver<SubscriptionResponse> observer) {
        sender.subscribe(toServerCall(observer));
    }

    /**
     * Stops sending the updates to the subscribers.
     */
    @Override
    public void close() {
        sender.close();
    }

    /**
     * Fetches information about the shards that have a session record or hold messages.
     *
     * <p>Leaves out the shard of the messages whose shard index is not set, which only
     * a defective client can write, because a {@code ShardInfo} must carry the index of its
     * shard. A session record always has its index set, as the record must have one too.
     */
    private ShardInfoList fetch() {
        var counts = new HashMap<>(inbox.counts());
        if (counts.remove(UNSET_INDEX) != null) {
            logger().atWarning()
                    .log(() -> "A shard whose index is not set is left out of the shard info.");
        }
        var shardListBuilder = ShardInfoList.newBuilder();
        for (var stored : sessions.readAll()) {
            var record = stored.getRecord();
            var count = counts.remove(record.getIndex());
            shardListBuilder.addShards(shardInfo(record, count == null ? 0 : count));
        }
        counts.forEach((index, count) -> shardListBuilder.addShards(shardInfo(index, count)));
        return shardListBuilder.build();
    }

    /**
     * Returns a new {@code ShardInfo} from the given {@code shardRecord} and {@code messagesCount}.
     */
    private static ShardInfo shardInfo(ShardSessionRecord shardRecord, int messagesCount) {
        return ShardInfo.newBuilder()
                .setIndex(shardRecord.getIndex())
                .setLastPicked(shardRecord.getWhenLastPicked())
                .setStatus(shardRecord.hasWorker() ? PICKED : NOT_PICKED)
                .setMessages(messagesCount)
                .build();
    }

    /**
     * Returns a new {@code ShardInfo} with the given {@code ShardIndex} and {@code messagesCount},
     * sets the shard status to {@code NOT_PICKED}, and doesn't set the last picked time.
     */
    private static ShardInfo shardInfo(ShardIndex index, int messagesCount) {
        return ShardInfo.newBuilder()
                .setIndex(index)
                .setStatus(NOT_PICKED)
                .setMessages(messagesCount)
                .build();
    }

    @Override
    public boolean healthy() {
        return healthy.get();
    }

    @Override
    public void healthy(boolean value) {
        healthy.set(value);
    }

    @Override
    public String name() {
        return AdminServiceGrpc.SERVICE_NAME;
    }
}
