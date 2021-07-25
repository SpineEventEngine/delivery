/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

/**
 * This package provides implementation of Redis-based storages.
 *
 * <p>Redis storage supports multitenancy. Data for each tenant is stored
 * in a "slice" represented by a data class prefixed with {@code Tenant}.
 */
@CheckReturnValue
@ParametersAreNonnullByDefault
package io.spine.server.storage.redis;

import com.google.errorprone.annotations.CheckReturnValue;

import javax.annotation.ParametersAreNonnullByDefault;
