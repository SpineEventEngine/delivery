/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery;

import com.google.common.testing.NullPointerTester;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.spine.server.delivery.DeliveryBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth8.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("`DeliveryBootstrapper` should")
final class DeliveryBootstrapperTest {

    @Test
    @DisplayName("be `NPE`-safe")
    void beNpeSafe() {
        assertThrows(
                NullPointerException.class,
                () -> DeliveryBootstrapper.newInstance().init()
        );
        NullPointerTester tester = new NullPointerTester();
        tester.testAllPublicInstanceMethods(DeliveryBootstrapper.newInstance());
    }

    @Test
    @DisplayName("bootstrap `DeliveryBuilder` configuration")
    void bootstrapDeliveryConfig() {
        ManagedChannel channel = new NoOpChannel();
        DeliveryBuilder builder = DeliveryBootstrapper.newInstance()
                .withChannel(channel)
                .init();
        assertThat(builder.inboxStorage())
                .isPresent();
        assertThat(builder.workRegistry())
                .isPresent();
    }

    /**
     * A test-only stub channel which does nothing.
     */
    private static class NoOpChannel extends ManagedChannel {

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
}
