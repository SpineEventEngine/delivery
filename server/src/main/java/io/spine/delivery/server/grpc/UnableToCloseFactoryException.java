/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.server.grpc;

/**
 * There was some problem attempting to close a {@code StorageFactory}.
 */
public final class UnableToCloseFactoryException extends RuntimeException {

    private static final long serialVersionUID = 7916208607916062536L;

    public UnableToCloseFactoryException(Throwable cause) {
        super(cause);
    }
}
