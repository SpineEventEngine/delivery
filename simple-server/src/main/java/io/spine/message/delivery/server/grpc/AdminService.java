/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.grpc;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import io.spine.logging.Logging;
import io.spine.message.delivery.admin.grpc.AdminServiceGrpc;
import io.spine.message.delivery.admin.grpc.ShardInfo;
import io.spine.message.delivery.admin.grpc.ShardInfoList;
import io.spine.message.delivery.server.ExtendedInboxStorage;
import io.spine.message.delivery.server.ShardRegistryStorage;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.InboxMessageId;
import io.spine.server.delivery.Page;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.ShardSessionRecord;
import io.spine.server.storage.StorageFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.spine.message.delivery.admin.grpc.ShardStatus.NOT_PICKED;
import static io.spine.message.delivery.admin.grpc.ShardStatus.PICKED;

/**
 * Allows getting information about the current state of the shards on the message delivery server.
 */
public class AdminService extends AdminServiceGrpc.AdminServiceImplBase
        implements Logging, NamedHealthAwareService {

    private static final int PAGE_SIZE = 400;

    private final AtomicBoolean healthy = new AtomicBoolean(true);

    private final ExtendedInboxStorage inboxStorage;

    private final ShardRegistryStorage shardStorage;

    public AdminService(StorageFactory factory) {
        inboxStorage = new ExtendedInboxStorage(factory, false);
        shardStorage = new ShardRegistryStorage(factory);
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

    /**
     * Fetches information about all shards and reports it to the given {@code observer}.
     */
    private ShardInfoList fetch() {
        Map<ShardIndex, Integer> messagesCount = messagesInShards();
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
     * Counts all elements starting from the given {@code firstPage} until last page in request.
     */
    private static int count(Page<InboxMessage> firstPage) {
        var currentPage = Optional.of(firstPage);
        int count = 0;
        do {
            Page<InboxMessage> page = currentPage.get();
            count += page.size();
            currentPage = page.next();
        } while (currentPage.isPresent());
        return count;
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
