/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import io.spine.server.BoundedContextBuilder;
import io.spine.testing.server.blackbox.ContextAwareTest;

/**
 * An abstract base for {@link DeliveryContext} tests.
 */
@SuppressWarnings("AbstractClassWithoutAbstractMethods") // that's intentional.
abstract class DeliveryTest extends ContextAwareTest {

    @Override
    protected BoundedContextBuilder contextBuilder() {
        return DeliveryContext.newBuilder()
                .contextClient(() -> {
                    throw new IllegalStateException("The client must not be called in this test.");
                })
                .context();
    }
}
