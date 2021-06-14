/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import io.spine.message.delivery.server.command.InboxStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;

@DisplayName("`DeliveryContextBuilder` should")
final class DeliveryContextBuilderTest {

    @Test
    @DisplayName("initialize `BoundedContext`")
    void initContext() {
        var boundedContext = new DeliveryContextBuilder().build();
        assertThat(boundedContext.hasEntitiesWithState(InboxStorage.class))
                .isTrue();
    }
}
