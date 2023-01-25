/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.grpc;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import io.spine.client.Client;
import io.spine.message.delivery.InboxMessageHolder;
import io.spine.message.delivery.ShardSessionRegistry;
import io.spine.message.delivery.admin.grpc.AdminServiceGrpc;
import io.spine.message.delivery.admin.grpc.ShardInfo;
import io.spine.message.delivery.admin.grpc.ShardInfoList;
import io.spine.server.delivery.ShardIndex;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.message.delivery.admin.grpc.ShardStatus.NOT_PICKED;
import static io.spine.message.delivery.admin.grpc.ShardStatus.PICKED;

public final class AdminService extends AdminServiceGrpc.AdminServiceImplBase {

    private final Client client;

    public AdminService(Supplier<Client> client) {
        this.client = checkNotNull(client.get());
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

    private ShardInfoList fetch() {
        Map<ShardIndex, Integer> messagesCount = messagesInShards();
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

    private static ShardInfo shardInfo(ShardIndex index, int messagesCount) {
        return ShardInfo
                .newBuilder()
                .setIndex(index)
                .setStatus(NOT_PICKED)
                .setMessages(messagesCount)
                .vBuild();
    }

    private static ShardInfo shardInfo(ShardSessionRegistry shard, int messagesCount) {
        return ShardInfo
                .newBuilder()
                .setIndex(shard.getId())
                .setLastPicked(shard.getWhenPicked())
                .setStatus(shard.hasWorker() ? PICKED : NOT_PICKED)
                .setMessages(messagesCount)
                .vBuild();
    }

    private Map<ShardIndex, Integer> messagesInShards() {
        Map<ShardIndex, Integer> messagesCount = new HashMap<>();
        ImmutableList<InboxMessageHolder> messages = readAllMessages();
        messages.forEach(message -> {
            ShardIndex shardIndex = message.getShard();
            messagesCount.put(shardIndex, messagesCount.getOrDefault(shardIndex, 0) + 1);
        });
        return messagesCount;
    }

    private ImmutableList<InboxMessageHolder> readAllMessages() {
        var query = InboxMessageHolder
                .query()
                .build();
        return client.asGuest()
                     .run(query);
    }

    private ImmutableList<ShardSessionRegistry> readShards() {
        ShardSessionRegistry.Query shardQuery = ShardSessionRegistry
                .query()
                .build();
        return client.asGuest()
                     .run(shardQuery);
    }
}
