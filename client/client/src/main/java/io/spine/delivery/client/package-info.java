/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

/**
 * Client implementation of Message Delivery.
 *
 * <p>The {@code delivery-client-base} module declares the same package.
 * Only one {@code package-info} wins at run time — which one depends on
 * the classpath order — so the annotations here must stay identical to
 * those of the {@code base} declaration.
 */
@CheckReturnValue
@JvmLoggingDomain("Delivery Client")
@NullMarked
package io.spine.delivery.client;

import com.google.errorprone.annotations.CheckReturnValue;

import io.spine.logging.JvmLoggingDomain;

import org.jspecify.annotations.NullMarked;
