/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.grpc;

import com.google.protobuf.Empty;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.spine.logging.Logging;
import io.spine.message.delivery.admin.grpc.AdminServiceGrpc;
import io.spine.message.delivery.admin.grpc.ShardInfo;
import io.spine.message.delivery.admin.grpc.ShardInfoList;
import io.spine.message.delivery.admin.grpc.ShardInfoUpdate;
import io.spine.message.delivery.server.ExtendedInboxStorage;
import io.spine.message.delivery.server.ReportingStorageFactory;
import io.spine.message.delivery.server.ShardRegistryStorage;
import io.spine.message.delivery.server.StorageSubscriber;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.InboxMessageId;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.ShardSessionRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.spine.message.delivery.admin.ShardInfoUpdates.messagesCountChangedTo;
import static io.spine.message.delivery.admin.ShardInfoUpdates.shardPicked;
import static io.spine.message.delivery.admin.ShardInfoUpdates.shardUnpicked;
import static io.spine.message.delivery.admin.grpc.ShardStatus.NOT_PICKED;
import static io.spine.message.delivery.admin.grpc.ShardStatus.PICKED;

/**
 * Allows getting information about the current state of the shards on the message delivery server.
 */
public final class AdminService extends AdminServiceGrpc.AdminServiceImplBase
        implements Logging, NamedHealthAwareService {

    private final AtomicBoolean healthy = new AtomicBoolean(true);

    private final ExtendedInboxStorage inboxStorage;

    private final ShardRegistryStorage shardStorage;

    /**
     * Subscribers of the {@code AdminService}.
     */
    private final Set<StreamObserver<ShardInfoUpdate>> subscribers = new HashSet<>();

    /**
     * Maps a {@code ShardIndex} to the number of messages currently available in the shard.
     *
     * <p>This field is created to provide the info about each shard. The information
     * updated using subscriptions that notify the service about changes in shards.
     *
     * <p>Accumulating the number is faster than fetching it on demand, because storage doesn't
     * support {@code count} queries, so fetching basically means read all the shards and then read
     * all the messages in each shard to count the number.
     *
     * <p>The {@code ConcurrentHashMap} is chosen because we want to protect write operations and
     * do not block read operations.
     *
     * @see #updateCount(ShardIndex, int)
     */
    private final ConcurrentHashMap<ShardIndex, Integer> messagesInShards;

    public AdminService(ReportingStorageFactory factory) {
        super();
        inboxStorage = new ExtendedInboxStorage(factory, false);
        shardStorage = new ShardRegistryStorage(factory);
        setupSubscribers(factory);
        messagesInShards = new ConcurrentHashMap<>(messagesInShards());
    }

    @SuppressWarnings("HandleMethodResult")
    // We do not need to unsubscribe until the server is off.
    private void setupSubscribers(ReportingStorageFactory factory) {
        factory.subscribe(ShardIndex.class, ShardSessionRecord.class, new ShardStorageSubscriber());
        factory.subscribe(InboxMessageId.class, InboxMessage.class, new InboxStorageSubscriber());
    }

    /**
     * Notifies all existent subscribers about the new {@code ShardInfoChange}.
     *
     * <p>If an error occurs when trying to notify subscriber it is marked as invalid and removed
     * from the subscribers list.
     */
    private void notifySubs(ShardInfoUpdate update) {
        _debug().log("Notifying %d subscribers about update.", subscribers.size());
        var invalidSubs = new ArrayList<StreamObserver<ShardInfoUpdate>>();
        for (var sub : subscribers) {
            try {
                sub.onNext(update);
            } catch (RuntimeException e) {
                _debug().withCause(e)
                        .log("Got exception, subscriber will be removed.");
                invalidSubs.add(sub);
                sub.onError(e);
            }
        }
        invalidSubs.forEach(subscribers::remove);
    }

    /**
     * Updates the {@code messagesInShards} for the given {@code index} on the given {@code delta}.
     *
     * @implNote In the implementation we rely on the fact that the {@code merge()}
     *         operation is atomic in the {@code ConcurrentHashMap}. If one update of the map
     *         is in progress other updates will be postponed by the time when
     *         the first update pass.
     *
     *         <p>In some rare cases if the {@code delta} is {@code -1} (message removed) and the
     *         map doesn't contain any info about the shard with the {@code index} the count will
     *         become {@code -1}. This means that we have events misordering and
     *         the “MessageRemoved” update arrived earlier than the “MessageWritten”.
     *         That's why we don't force the count to be always positive, hoping that
     *         the “MessageWritten” will arrive shortly and will make
     *         the state consistent — ({@code 0}).
     */
    private int updateCount(ShardIndex index, int delta) {
        return messagesInShards.merge(index, delta, Integer::sum);
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
    subscribeToShardUpdates(Empty request, StreamObserver<ShardInfoUpdate> observer) {
        subscribers.add(observer);
        toServerCall(observer).setOnCancelHandler(() -> subscribers.remove(observer));
        _debug().log("Added one subscriber, current number of subscribers = %d",
                     subscribers.size());
    }

    /**
     * Fetches information about all shards.
     */
    private ShardInfoList fetch() {
        Map<ShardIndex, Integer> messagesCount = new HashMap<>(messagesInShards);
        var shards = shardStorage.readAll();
        var shardListBuilder = ShardInfoList.newBuilder();
        shards.forEachRemaining(shard -> {
            ShardInfo info = shardInfo(shard, messagesCount.getOrDefault(shard.getIndex(), 0));
            shardListBuilder.addShards(info);
            messagesCount.remove(shard.getIndex());
        });
        messagesCount.forEach((key, value) -> shardListBuilder.addShards(shardInfo(key, value)));
        return shardListBuilder.vBuild();
    }

    /**
     * Reads all messages from the storage and counts the number of messages in each shard.
     */
    private Map<ShardIndex, Integer> messagesInShards() {
        Map<ShardIndex, Integer> messagesCount = new HashMap<>();
        Iterator<InboxMessage> messages = inboxStorage.readAll();
        messages.forEachRemaining(message -> {
            InboxMessageId inboxMessageId = message.getId();
            ShardIndex shardIndex = inboxMessageId.getIndex();
            messagesCount.put(shardIndex, messagesCount.getOrDefault(shardIndex, 0) + 1);
        });
        return messagesCount;
    }

    /**
     * Returns a new {@code ShardInfo} from the given {@code shardRecord} and {@code messagesCount}.
     */
    private static ShardInfo shardInfo(ShardSessionRecord shardRecord, int messagesCount) {
        return ShardInfo
                .newBuilder()
                .setIndex(shardRecord.getIndex())
                .setLastPicked(shardRecord.getWhenLastPicked())
                .setStatus(shardRecord.hasWorker() ? PICKED : NOT_PICKED)
                .setMessages(messagesCount)
                .vBuild();
    }

    /**
     * Returns a new {@code ShardInfo} with the given {@code ShardIndex} and {@code messagesCount},
     * sets the shard status to {@code NOT_PICKED}, and doesn't set the last picked time.
     */
    private static ShardInfo shardInfo(ShardIndex index, int messagesCount) {
        return ShardInfo
                .newBuilder()
                .setIndex(index)
                .setStatus(NOT_PICKED)
                .setMessages(messagesCount)
                .vBuild();
    }

    /**
     * Casts the given {@code observer} to the {@code ServerCallStreamObserver}.
     *
     * <p>According to the {@link ServerCallStreamObserver} docs it's safe to cast
     * {@code StreamObserver} to {@code ServerCallStreamObserver} in server side implementation
     * of the service.
     */
    private static ServerCallStreamObserver<ShardInfoUpdate>
    toServerCall(StreamObserver<ShardInfoUpdate> observer) {
        return (ServerCallStreamObserver<ShardInfoUpdate>) observer;
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

    /**
     * Subscriber that tracks the inbox changes and notifies subscribers of the service
     * about these changes.
     */
    private final class InboxStorageSubscriber
            implements StorageSubscriber<InboxMessageId, InboxMessage> {

        @Override
        public void onWrite(InboxMessageId id, InboxMessage message) {
            ShardIndex index = id.getIndex();
            notifySubs(messagesCountChangedTo(index, updateCount(index, 1)));
        }

        @Override
        public void onDelete(InboxMessageId id) {
            ShardIndex index = id.getIndex();
            notifySubs(messagesCountChangedTo(index, updateCount(index, -1)));
        }
    }

    /**
     * Subscriber that tracks shard changes and notifies subscribers of the service
     * about these changes.
     */
    private final class ShardStorageSubscriber
            implements StorageSubscriber<ShardIndex, ShardSessionRecord> {

        @Override
        public void onWrite(ShardIndex id, ShardSessionRecord message) {
            ShardInfoUpdate update = message.hasWorker() ?
                                     shardPicked(id, message.getWhenLastPicked()) :
                                     shardUnpicked(id);
            notifySubs(update);
        }

        @Override
        public void onDelete(ShardIndex id) {
            // We don't delete shard records from storage.
        }
    }
}
