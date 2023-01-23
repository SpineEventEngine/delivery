/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server.grpc;

/**
 * There was some problem attempting to close {@code StorageFactory}.
 */
public class UnableToCloseFactoryException extends RuntimeException {

    private static final long serialVersionUID = 7916208607916062536L;

    public UnableToCloseFactoryException(Throwable cause) {
        super(cause);
    }
}
