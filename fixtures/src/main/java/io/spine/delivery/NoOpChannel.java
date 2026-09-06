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

package io.spine.delivery;

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;

import java.util.concurrent.TimeUnit;

/**
 * A test-only stub channel that does nothing.
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
    public <T, R> ClientCall<T, R> newCall(
            MethodDescriptor<T, R> methodDescriptor,
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
