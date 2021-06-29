/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import io.spine.message.delivery.InboxMessageHolder;
import io.spine.message.delivery.InboxModifier;
import io.spine.message.delivery.ShardSessionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.testing.Assertions.assertHasPrivateParameterlessCtor;

@DisplayName("`DeliveryContext.Builder` should")
final class DeliveryContextBuilderTest {

    @Test
    @DisplayName("have private constructor")
    void havePrivateConstructor() {
        assertHasPrivateParameterlessCtor(DeliveryContext.Builder.class);
    }

    @Test
    @DisplayName("initialize `BoundedContext`")
    void initContext() {
        var boundedContext = DeliveryContext.newBuilder()
                .context()
                .build();
        assertThat(boundedContext.hasEntitiesWithState(InboxMessageHolder.class))
                .isTrue();
        assertThat(boundedContext.hasEntitiesWithState(ShardSessionRegistry.class))
                .isTrue();
        assertThat(boundedContext.hasEntitiesWithState(InboxModifier.class))
                .isTrue();
    }
}
