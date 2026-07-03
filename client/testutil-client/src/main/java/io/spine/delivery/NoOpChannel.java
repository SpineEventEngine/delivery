/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery;

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;

import java.util.concurrent.TimeUnit;

/**
 * A test-only stub channel which does nothing.
 */
public final class NoOpChannel extends ManagedChannel {

    @Override
    public ManagedChannel shutdown() {
        throw notSupported();
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public boolean isTerminated() {
        return false;
    }

    @Override
    public ManagedChannel shutdownNow() {
        throw notSupported();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return false;
    }

    @Override
    public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(
            MethodDescriptor<RequestT, ResponseT> methodDescriptor,
            CallOptions callOptions) {
        throw notSupported();
    }

    @Override
    public String authority() {
        throw notSupported();
    }

    private static UnsupportedOperationException notSupported() {
        throw new UnsupportedOperationException("Not supported.");
    }
}
