/*
 * Copyright (c) 2000-2023 TeamDev Ltd. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

/**
 * Server implementation of {@code Delivery} Bounded Context.
 */
@BoundedContext(DeliveryContext.NAME)
@CheckReturnValue
@ParametersAreNonnullByDefault
package io.spine.message.delivery.server;

import com.google.errorprone.annotations.CheckReturnValue;
import io.spine.core.BoundedContext;

import javax.annotation.ParametersAreNonnullByDefault;
