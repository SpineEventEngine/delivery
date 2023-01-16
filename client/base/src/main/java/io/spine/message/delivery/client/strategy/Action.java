/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client.strategy;

/**
 * An operation that is applied when {@code VoidRequest} failed.
 */
public interface Action {

    /**
     * Executes the action.
     */
    void execute();
}
