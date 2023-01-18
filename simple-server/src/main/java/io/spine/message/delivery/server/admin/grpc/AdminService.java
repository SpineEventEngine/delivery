/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.admin.grpc;

import io.grpc.stub.StreamObserver;
import io.spine.logging.Logging;
import io.spine.message.delivery.admin.grpc.AdminServiceGrpc;
import io.spine.message.delivery.admin.grpc.ShardInfo;
import io.spine.message.delivery.admin.grpc.ShardInfoList;
import io.spine.message.delivery.admin.grpc.SubscriptionRequest;
import io.spine.message.delivery.server.ExtendedInboxStorage;
import io.spine.message.delivery.server.ShardRegistryStorage;
import io.spine.message.delivery.server.grpc.NamedHealthAwareService;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.Page;
import io.spine.server.delivery.ShardSessionRecord;
import io.spine.server.storage.StorageFactory;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.protobuf.util.Durations.toSeconds;
import static io.spine.message.delivery.admin.grpc.ShardStatus.PICKED;
import static io.spine.message.delivery.admin.grpc.ShardStatus.UNPICKED;
import static java.util.concurrent.Executors.newScheduledThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Allows getting information about .
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
    @SuppressWarnings("FutureReturnValueIgnored")
    public void shardsInfo(SubscriptionRequest request, StreamObserver<ShardInfoList> response) {
        var service = newScheduledThreadPool(1);
        long seconds = toSeconds(request.getDuration());
        service.scheduleWithFixedDelay(() -> fetchAndReport(response), 0, seconds, SECONDS);
    }

    /**
     * Fetches information about all shards and reports it to the given {@code observer}.
     */
    private void fetchAndReport(StreamObserver<ShardInfoList> observer) {
        try {
            var shards = shardStorage.readAll();
            var shardInfoListBuilder = ShardInfoList.newBuilder();
            shards.forEachRemaining((shard) -> {
                int count = count(inboxStorage.readAll(shard.getIndex(), PAGE_SIZE));
                var shardInfo = shardInfo(shard, count);
                shardInfoListBuilder.addShards(shardInfo);
            });
            observer.onNext(shardInfoListBuilder.vBuild());
        } catch (RuntimeException e) {
            observer.onError(e);
        }
    }

    /**
     * Returns a new {@code ShardInfo} from the given {@code shardRecord} and {@code messagesCount}.
     */
    private static ShardInfo shardInfo(ShardSessionRecord shardRecord, int messagesCount) {
        return ShardInfo
                .newBuilder()
                .setIndex(shardRecord.getIndex())
                .setStatus(shardRecord.hasWorker() ? PICKED : UNPICKED)
                .setMessages(messagesCount)
                .vBuild();
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
