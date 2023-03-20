/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.grpc;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Empty;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.spine.base.EventMessage;
import io.spine.client.Client;
import io.spine.client.Subscription;
import io.spine.logging.Logging;
import io.spine.message.delivery.CurrentShardState;
import io.spine.message.delivery.InboxMessageHolder;
import io.spine.message.delivery.admin.grpc.AdminServiceGrpc;
import io.spine.message.delivery.admin.grpc.ShardInfo;
import io.spine.message.delivery.admin.grpc.ShardInfoList;
import io.spine.message.delivery.admin.grpc.ShardInfoUpdate;
import io.spine.message.delivery.event.MessageRemoved;
import io.spine.message.delivery.event.MessageWritten;
import io.spine.message.delivery.event.ShardPickedUp;
import io.spine.message.delivery.event.ShardReleased;
import io.spine.server.delivery.ShardIndex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Sets.newConcurrentHashSet;
import static io.spine.message.delivery.admin.ShardInfoUpdates.messagesCountChangedTo;
import static io.spine.message.delivery.admin.ShardInfoUpdates.shardPicked;
import static io.spine.message.delivery.admin.ShardInfoUpdates.shardUnpicked;
import static io.spine.message.delivery.admin.grpc.ShardStatus.NOT_PICKED;
import static io.spine.message.delivery.admin.grpc.ShardStatus.PICKED;

/**
 * Allows getting information about the current state of the shards on the message delivery server.
 */
public final class AdminService extends AdminServiceGrpc.AdminServiceImplBase implements Logging {

    private final Client client;

    /**
     * Subscribers of the {@code AdminService}.
     *
     * <p>We use {@code newConcurrentHashSet()} to avoid {@code ConcurrentModificationException}
     * in cases when we are iterating over the set to notify subscribers and a new subscriber
     * arrives and the neighbor thread modifies the collection. Also, it's possible that one thread
     * iterates over the set to notify subscribers and one subscriber is being closed at this moment
     * and removed from the collection. In the last scenario if we started to iterate over
     * the collection before the subscriber is closed and removed most probably we will get
     * the closed subscriber during the iteration and will try to notify it. But this will not lead
     * to a problem because the {@link #notifySubs(ShardInfoUpdate)} handles invalid subscribers
     * removing them from the collection, which is not a problem also, even if we will try
     * to remove an already removed element.
     */
    private final Set<StreamObserver<ShardInfoUpdate>> subscribers = newConcurrentHashSet();

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

    /**
     * Creates a new {@code AdminService} with the given {@code client}.
     */
    public AdminService(Client client) {
        super();
        this.client = checkNotNull(client);
        setupEventListener();
        messagesInShards = new ConcurrentHashMap<>(messagesInShards());
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
     * Setups event listeners for Shard-related events to track shard status changes.
     *
     * @implNote We are not preserving {@code Subscription}s returned by the {@code on()}
     *         method because there is no need to unsubscribe until the app shut down.
     */
    private void setupEventListener() {
        on(ShardPickedUp.class, pickedUp ->
                notifySubs(shardPicked(pickedUp.getShard(), pickedUp.getWhenPicked())));
        on(ShardReleased.class, released -> notifySubs(shardUnpicked(released.getShard())));
        on(MessageWritten.class, written -> {
            ShardIndex index = written
                    .getMessage()
                    .shardIndex();
            notifySubs(messagesCountChangedTo(index, updateCount(index, 1)));
        });
        on(MessageRemoved.class, removed -> {
            ShardIndex index = removed
                    .getMessage()
                    .shardIndex();
            notifySubs(messagesCountChangedTo(index, updateCount(index, -1)));
        });
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

    /**
     * Subscribes on the given {@code event} class.
     */
    @CanIgnoreReturnValue
    private <E extends EventMessage> Subscription on(Class<E> event, Consumer<E> handler) {
        return this.client
                .asGuest()
                .subscribeToEvent(event)
                .observe(handler)
                .post();
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
     * Fetches information about all shards.
     */
    private ShardInfoList fetch() {
        Map<ShardIndex, Integer> messagesCount = new HashMap<>(messagesInShards);
        var shards = readShards();
        var shardListBuilder = ShardInfoList.newBuilder();
        shards.forEach(shard -> {
            ShardInfo info = shardInfo(shard, messagesCount.getOrDefault(shard.getId(), 0));
            shardListBuilder.addShards(info);
            messagesCount.remove(shard.getId());
        });
        messagesCount.forEach((key, value) -> shardListBuilder.addShards(shardInfo(key, value)));
        return shardListBuilder.vBuild();
    }

    /**
     * Reads all messages from the storage and counts the number of messages in each shard.
     */
    private Map<ShardIndex, Integer> messagesInShards() {
        Map<ShardIndex, Integer> messagesCount = new HashMap<>();
        ImmutableList<InboxMessageHolder> messages = readAllMessages();
        messages.forEach(message -> {
            ShardIndex shardIndex = message.getShard();
            messagesCount.put(shardIndex, messagesCount.getOrDefault(shardIndex, 0) + 1);
        });
        return messagesCount;
    }

    /**
     * Queries all {@code InboxMessageHolder}s.
     */
    private ImmutableList<InboxMessageHolder> readAllMessages() {
        var query = InboxMessageHolder
                .query()
                .build();
        return client.asGuest()
                     .run(query);
    }

    /**
     * Queries all {@code ShardSessionHolder}s.
     */
    private ImmutableList<CurrentShardState> readShards() {
        CurrentShardState.Query shardQuery = CurrentShardState
                .query()
                .build();
        return client.asGuest()
                     .run(shardQuery);
    }

    /**
     * Returns a new {@code ShardInfo} from the given {@code shard} and {@code messagesCount}.
     */
    private static ShardInfo shardInfo(CurrentShardState shard, int messagesCount) {
        return ShardInfo
                .newBuilder()
                .setIndex(shard.getId())
                .setLastPicked(shard.getWhenLastPicked())
                .setStatus(shard.hasWorker() ? PICKED : NOT_PICKED)
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
}
