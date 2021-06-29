/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery;

import com.google.errorprone.annotations.Immutable;
import io.spine.annotation.GeneratedMixin;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.InboxMessageId;

/**
 * Provides helper APIs for messages containing {@code InboxMessage} in them.
 */
@Immutable
@GeneratedMixin
public interface WithInboxMessage {

    /**
     * Returns the {@code InboxMessage}.
     *
     * @implNote This method is implemented in the deriving Protobuf messages.
     */
    InboxMessage getMessage();

    /**
     * Returns the ID of the associated {@code InboxMessage}.
     */
    default InboxMessageId messageId() {
        return getMessage().getId();
    }
}
