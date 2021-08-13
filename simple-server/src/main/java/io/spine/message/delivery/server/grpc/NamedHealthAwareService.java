/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.grpc;

/**
 * A gRPC service aware of its {@linkplain NameAware name}
 * and {@linkplain HealthAware health}.
 */
public interface NamedHealthAwareService extends NameAware, HealthAware {
}
