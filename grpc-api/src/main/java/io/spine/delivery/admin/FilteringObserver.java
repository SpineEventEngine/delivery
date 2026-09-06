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

package io.spine.delivery.admin;

import io.grpc.stub.ServerCallStreamObserver;
import io.spine.delivery.admin.grpc.SubscriptionResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Filters out updates that were sent before the response had been acknowledged.
 *
 * <p>This observer guarantees that the {@code SubscriptionResponse} acknowledging the subscription
 * arrives first. All the updates that may be posted before acknowledging the response
 * are omitted by this observer.
 */
public final class FilteringObserver extends ServerCallStreamObserver<SubscriptionResponse> {

    private final ServerCallStreamObserver<SubscriptionResponse> delegate;

    private final AtomicBoolean isAcknowledged = new AtomicBoolean(false);

    /**
     * Creates a new {@code FilteringObserver} with the given {@code delegate}.
     */
    public FilteringObserver(ServerCallStreamObserver<SubscriptionResponse> delegate) {
        super();
        checkNotNull(delegate);
        this.delegate = delegate;
    }

    @Override
    public void onNext(SubscriptionResponse value) {
        if (isAcknowledged.get()) {
            delegate.onNext(value);
        } else {
            if (value.hasCreated()) {
                isAcknowledged.set(true);
                delegate.onNext(value);
            }
        }
    }

    @Override
    public boolean isCancelled() {
        return delegate.isCancelled();
    }

    @Override
    public void setOnCancelHandler(Runnable onCancelHandler) {
        delegate.setOnCancelHandler(onCancelHandler);
    }

    @Override
    public void setCompression(String compression) {
        delegate.setCompression(compression);
    }

    @Override
    public boolean isReady() {
        return delegate.isReady();
    }

    @Override
    public void setOnReadyHandler(Runnable onReadyHandler) {
        delegate.setOnReadyHandler(onReadyHandler);
    }

    @Override
    public void disableAutoInboundFlowControl() {
        delegate.disableAutoInboundFlowControl();
    }

    @Override
    public void request(int count) {
        delegate.request(count);
    }

    @Override
    public void setMessageCompression(boolean enable) {
        delegate.setMessageCompression(enable);
    }

    @Override
    public void onError(Throwable t) {
        delegate.onError(t);
    }

    @Override
    public void onCompleted() {
        delegate.onCompleted();
    }
}
