/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.admin.security;

import static io.spine.util.Preconditions2.checkNotEmptyOrBlank;

/**
 * Username and password credentials that are used in the {@code HTTP Basic Auth} scheme.
 */
public final class HttpBasicAuthCredentials {

    private final String username;

    private final String password;

    public HttpBasicAuthCredentials(String username, String password) {
        this.username = checkNotEmptyOrBlank(username);
        this.password = checkNotEmptyOrBlank(password);
    }

    /**
     * Returns the username.
     */
    String username() {
        return username;
    }

    /**
     * Returns the password.
     */
    String password() {
        return password;
    }
}
