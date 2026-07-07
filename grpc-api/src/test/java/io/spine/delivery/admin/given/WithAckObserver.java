/*
 * Copyright 2026, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
            throw new IllegalStateException(e);
        }
    }
}
