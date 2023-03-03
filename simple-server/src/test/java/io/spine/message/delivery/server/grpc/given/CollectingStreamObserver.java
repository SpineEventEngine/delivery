/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.grpc.given;

import com.google.common.collect.ImmutableList;
import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Stream Observer that preserves all sent data.
 *
 * @param <T>
 *         type of values returned by the observing stream
 */
public class CollectingStreamObserver<T> implements StreamObserver<T> {

    private final List<T> values = new ArrayList<>();

    private Throwable error;

    private boolean isCompleted = false;

    @Override
    public void onNext(T value) {
        this.values.add(value);
    }

    @Override
    public void onError(Throwable e) {
        this.error = e;
    }

    @Override
    public void onCompleted() {
        this.isCompleted = true;
    }

    /**
     * Returns {@code true} if the {@code onCompleted()} method called on this observer
     * or {@code false} otherwise.
     */
    public boolean isCompleted() {
        return this.isCompleted;
    }

    /**
     * Returns all data received through the {@code onNext()} method.
     */
    public ImmutableList<T> values() {
        return ImmutableList.copyOf(values);
    }

    /**
     * Returns an error consumed by the observer, or empty {@code Optional} if there is no error.
     */
    public Optional<Throwable> error() {
        return Optional.ofNullable(error);
    }
}
