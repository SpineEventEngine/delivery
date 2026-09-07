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
import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.Empty;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.grpc.stub.StreamObserver;
import io.spine.delivery.admin.grpc.ShardInfoUpdate;
import io.spine.delivery.admin.grpc.SubscriptionResponse;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.util.concurrent.ExecutionException;

/**
 * An observer that allows waiting for the subscription to be acknowledged
 * and {@linkplain #cancel() cancelling} the subscription call.
 */
@VisibleForTesting
public class WithAckObserver implements ClientResponseObserver<Empty, SubscriptionResponse> {

    private final SettableFuture<Boolean> ack = SettableFuture.create();

    private final StreamObserver<ShardInfoUpdate> observer;

    private @MonotonicNonNull ClientCallStreamObserver<Empty> call;

    /**
     * Creates a new {@code WithAckObserver} with the given {@code observer} as its update source.
     */
    public WithAckObserver(StreamObserver<ShardInfoUpdate> observer) {
        this.observer = observer;
    }

    @Override
    public void beforeStart(ClientCallStreamObserver<Empty> requestStream) {
        this.call = requestStream;
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
        } catch (InterruptedException e) {
            Thread.currentThread()
                  .interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Cancels the subscription call, if it has been started.
     *
     * <p>Ends the server-streaming call from the client side, so that the test
     * teardown does not have to force-kill it together with the channel.
     */
    public void cancel() {
        if (call != null) {
            call.cancel("The test is over.", null);
        }
    }
}
