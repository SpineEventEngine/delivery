/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

/**
 * This package provides an implementation of Redis-based storages.
 *
 * <p>Redis storage supports multitenancy. Data for each tenant is stored
 * in a "slice" represented by a data class prefixed with {@code Tenant}.
 */
@CheckReturnValue
@NullMarked
package io.spine.server.storage.redis;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
