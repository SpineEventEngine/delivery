/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import io.spine.server.CommandService;
import io.spine.server.QueryService;
import io.spine.server.SubscriptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;

@DisplayName("`DeliveryContext` should")
final class DeliveryContextTest {

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
                .isInstanceOf(DeliveryContext.Builder.class);
    }

    @Test
    @DisplayName("expose context-related APIs")
    void exposeApis() {
        var context = DeliveryContext.newBuilder()
                .contextClient(() -> {
                    throw new IllegalStateException("The client must not be called in this test.");
                })
                .build();
        assertThat(context.commandService())
                .isInstanceOf(CommandService.class);
        assertThat(context.queryService())
                .isInstanceOf(QueryService.class);
        assertThat(context.subscriptionService())
                .isInstanceOf(SubscriptionService.class);
    }
}
