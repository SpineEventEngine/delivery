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

import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An observer that transforms received values and passes them to the delegate.
 *
 * @param <I>
 *         type of observer values
 * @param <O>
 *         type of output values
 */
public class TransformingStreamObserver<I, O> extends ServerCallStreamObserver<I> {

    private final ServerCallStreamObserver<O> delegate;
    private final Function<I, O> mapper;

    public TransformingStreamObserver(ServerCallStreamObserver<O> delegate, Function<I, O> mapper) {
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
