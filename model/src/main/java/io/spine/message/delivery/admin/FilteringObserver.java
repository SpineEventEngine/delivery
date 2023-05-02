/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.admin;

import io.grpc.stub.ServerCallStreamObserver;
import io.spine.message.delivery.admin.grpc.SubscriptionResponse;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Filters out updates that were sent before the response had been acknowledged.
 *
 * <p>This observer guarantees that the {@code SubscriptionResponse} acknowledging the subscription
 * arrives first. All the updates that may be posted before acknowledging response
 * are omitted by this observer.
 */
public final class FilteringObserver
        extends DelegatingServerCallStreamObserver<SubscriptionResponse> {

    private final AtomicBoolean isAcknowledged = new AtomicBoolean(false);

    /**
     * Creates a new {@code FilteringObserver} with the given {@code delegate}.
     */
    public FilteringObserver(ServerCallStreamObserver<SubscriptionResponse> delegate) {
        super(delegate);
    }

    @Override
    public void onNext(SubscriptionResponse value) {
        if (isAcknowledged.get()) {
            super.onNext(value);
        } else {
            if (value.hasCreated()) {
                isAcknowledged.set(true);
                super.onNext(value);
            }
        }
    }
}
