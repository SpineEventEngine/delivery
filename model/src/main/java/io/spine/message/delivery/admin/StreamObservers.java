/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.admin;

import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Utility that provides some usefully operations with {@linkplain StreamObserver}s.
 */
public final class StreamObservers {

    /**
     * Prevents instantiation.
     */
    private StreamObservers() {
    }

    /**
     * Casts the given {@code observer} to the {@code ServerCallStreamObserver}.
     *
     * <p>According to the {@link ServerCallStreamObserver} docs it's safe to cast
     * {@code StreamObserver} to {@code ServerCallStreamObserver} in server side implementation
     * of the service.
     */
    public static <T> ServerCallStreamObserver<T> toServerCall(StreamObserver<T> observer) {
        checkNotNull(observer);
        return (ServerCallStreamObserver<T>) observer;
    }
}
