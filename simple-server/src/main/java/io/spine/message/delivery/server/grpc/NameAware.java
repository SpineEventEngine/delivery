/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.grpc;

/**
 * A service aware of its canonical gRPC name.
 */
public interface NameAware {

    /**
     * Returns the canonical gRPC name of the service.
     */
    String name();
}
