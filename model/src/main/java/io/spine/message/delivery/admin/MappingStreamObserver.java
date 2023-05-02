/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.admin;

import io.grpc.stub.ServerCallStreamObserver;

import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An observer that transforms received values and passes them to the delegate.
 *
 * @param <I>
 *         Type of observer values
 * @param <O>
 *         Type of output values
 */
public class MappingStreamObserver<I, O> extends ServerCallStreamObserver<I> {

    private final ServerCallStreamObserver<O> delegate;
    private final Function<I, O> mapper;

    public MappingStreamObserver(ServerCallStreamObserver<O> delegate, Function<I, O> mapper) {
        super();
        checkNotNull(delegate);
        checkNotNull(mapper);
        this.delegate = delegate;
        this.mapper = mapper;
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
    public void onNext(I value) {
        delegate.onNext(mapper.apply(value));
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
