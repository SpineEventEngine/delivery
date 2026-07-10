/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.admin.given;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import io.spine.delivery.admin.grpc.AdminServiceGrpc;
import io.spine.delivery.admin.grpc.ShardInfoList;

import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An implementation of the {@code AdminService} that returns predefined results.
 *
 * <p>This class is for testing purposes only.
 */
@VisibleForTesting
public final class LocalAdminService extends AdminServiceGrpc.AdminServiceImplBase {

    private final Consumer<StreamObserver<ShardInfoList>> resultGenerator;

    /**
     * Creates a new {@code LocalAdminService} that performs the given action
     * when the {@link #getShardInfo(Empty, StreamObserver)} called.
     */
    public static LocalAdminService onGetShardInfo(Consumer<StreamObserver<ShardInfoList>> action) {
        checkNotNull(action);
        return new LocalAdminService(action);
    }

    private LocalAdminService(Consumer<StreamObserver<ShardInfoList>> generator) {
        resultGenerator = generator;
    }

    /**
     * Returns predefined result.
     */
    @Override
    public void getShardInfo(Empty request, StreamObserver<ShardInfoList> responseObserver) {
        resultGenerator.accept(responseObserver);
    }
}
