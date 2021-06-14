/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.testing.Assertions.assertHasPrivateParameterlessCtor;

@DisplayName("`DeliveryContext` should")
final class DeliveryContextTest {

    @Test
    @DisplayName("have private constructor")
    void havePrivateConstructor() {
        assertHasPrivateParameterlessCtor(DeliveryContext.class);
    }

    @Test
    @DisplayName("expose the context name")
    void exposeName() {
        assertThat(DeliveryContext.NAME)
                .isEqualTo("Delivery");
    }

    @Test
    @DisplayName("expose the context builder")
    void exposeBuilder() {
        assertThat(DeliveryContext.newBuilder())
                .isInstanceOf(DeliveryContextBuilder.class);
    }
}
