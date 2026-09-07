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

package io.spine.delivery.admin.given;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import io.spine.delivery.admin.grpc.AdminServiceGrpc;
import io.spine.delivery.admin.grpc.ShardInfoList;
import io.spine.delivery.admin.grpc.SubscriptionResponse;

import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An implementation of the {@code AdminService} that returns predefined results.
 *
 * <p>This class is for testing purposes only.
 */
@VisibleForTesting
public final class LocalAdminService extends AdminServiceGrpc.AdminServiceImplBase {

    private final Consumer<StreamObserver<ShardInfoList>> shardInfo;
    private final Consumer<StreamObserver<SubscriptionResponse>> shardUpdates;

    /**
     * Creates a new {@code LocalAdminService} that performs the given action
     * when the {@link #getShardInfo(Empty, StreamObserver)} called.
     */
    public static LocalAdminService onGetShardInfo(Consumer<StreamObserver<ShardInfoList>> action) {
        checkNotNull(action);
        return new LocalAdminService(action, completeImmediately());
    }

    /**
     * Creates a new {@code LocalAdminService} that performs the given action
     * when the {@link #subscribeToShardUpdates(Empty, StreamObserver)} called.
     */
    public static LocalAdminService
    onSubscribeToShardUpdates(Consumer<StreamObserver<SubscriptionResponse>> action) {
        checkNotNull(action);
        return new LocalAdminService(completeImmediately(), action);
    }

    private LocalAdminService(Consumer<StreamObserver<ShardInfoList>> shardInfo,
                              Consumer<StreamObserver<SubscriptionResponse>> shardUpdates) {
        this.shardInfo = shardInfo;
        this.shardUpdates = shardUpdates;
    }

    /**
     * Returns an action that completes the call without emitting a response.
     */
    private static <T> Consumer<StreamObserver<T>> completeImmediately() {
        return StreamObserver::onCompleted;
    }

    /**
     * Returns a predefined result.
     */
    @Override
    public void getShardInfo(Empty request, StreamObserver<ShardInfoList> responseObserver) {
        shardInfo.accept(responseObserver);
    }

    /**
     * Returns predefined updates.
     */
    @Override
    public void subscribeToShardUpdates(Empty request,
                                        StreamObserver<SubscriptionResponse> responseObserver) {
        shardUpdates.accept(responseObserver);
    }
}
