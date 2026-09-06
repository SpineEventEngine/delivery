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
import io.grpc.stub.StreamObserver;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Utility that provides some useful operations with {@linkplain StreamObserver}s.
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
     * {@code StreamObserver} to {@code ServerCallStreamObserver} in a server-side implementation
     * of the service.
     */
    public static <T> ServerCallStreamObserver<T> toServerCall(StreamObserver<T> observer) {
        checkNotNull(observer);
        return (ServerCallStreamObserver<T>) observer;
    }
}
