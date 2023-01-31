/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.admin.security;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Username and password credentials that is used in {@code HTTP Basic Auth} scheme.
 */
public final class HttpBasicAuthCredentials {

    private final String username;

    private final String password;

    public HttpBasicAuthCredentials(String username, String password) {
        this.username = checkNotNull(username);
        this.password = checkNotNull(password);
    }

    /**
     * Returns username.
     */
    String username() {
        return username;
    }

    /**
     * Returns password.
     */
    String password() {
        return password;
    }
}
