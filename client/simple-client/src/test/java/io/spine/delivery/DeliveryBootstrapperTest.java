/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery;

import com.google.common.testing.NullPointerTester;
import io.spine.server.delivery.DeliveryBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
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
        DeliveryBuilder builder = DeliveryBootstrapper.newInstance()
                .withChannel(NoOpChannel::new)
                .init();
        assertThat(builder.hasInboxStorage())
                .isTrue();
        assertThat(builder.hasWorkRegistry())
                .isTrue();
    }
}
