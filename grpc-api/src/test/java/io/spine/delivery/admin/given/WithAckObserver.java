/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.admin.given;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.SettableFuture;
import io.grpc.stub.StreamObserver;
import io.spine.delivery.admin.grpc.ShardInfoUpdate;
import io.spine.delivery.admin.grpc.SubscriptionResponse;

import java.util.concurrent.ExecutionException;

/**
 * An observer that allows waiting for the subscription to be acknowledged.
 */
@VisibleForTesting
public class WithAckObserver implements StreamObserver<SubscriptionResponse> {

    private final SettableFuture<Boolean> ack = SettableFuture.create();

    private final StreamObserver<ShardInfoUpdate> observer;

    /**
     * Creates a new {@code WithAckObserver} with the given {@code observer} as its update source.
     */
    public WithAckObserver(StreamObserver<ShardInfoUpdate> observer) {
        this.observer = observer;
    }

    @Override
    public void onNext(SubscriptionResponse value) {
        if (value.hasCreated()) {
            ack.set(true);
        } else {
            if (ack.isDone()) {
                observer.onNext(value.getUpdate());
            }
        }
    }

    @Override
    public void onError(Throwable t) {
        observer.onError(t);
    }

    @Override
    public void onCompleted() {
        observer.onCompleted();
    }

    /**
     * Blocks the current thread until the {@code SubscriptionResponse} containing
     * an acknowledgement is received.
     */
    public void waitForAcknowledgment() {
        try {
            ack.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
