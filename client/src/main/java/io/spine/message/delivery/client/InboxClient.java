/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import io.spine.message.delivery.event.MessageWritten;
import io.spine.server.delivery.InboxMessage;

import java.util.Optional;

/**
 * A client for working with the inbox.
 */
public interface InboxClient {

    /**
     * Tries to write a new {@code message} to the inbox.
     */
    Optional<MessageWritten> writeMessage(InboxMessage message);
}
