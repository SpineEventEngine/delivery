/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.admin.security;

import static io.spine.util.Preconditions2.checkNotEmptyOrBlank;

/**
 * Username and password credentials that is used in {@code HTTP Basic Auth} scheme.
 */
public final class HttpBasicAuthCredentials {

    private final String username;

    private final String password;

    public HttpBasicAuthCredentials(String username, String password) {
        this.username = checkNotEmptyOrBlank(username);
        this.password = checkNotEmptyOrBlank(password);
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
