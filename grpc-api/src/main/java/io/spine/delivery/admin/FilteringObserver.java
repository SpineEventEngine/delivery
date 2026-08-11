/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
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
