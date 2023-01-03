/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.ShardIndex;

/**
 * Modifies the remote Inbox state.
 */
public interface InboxModifier {

    /**
     * Writes a new {@code message} to the inbox.
     */
    void writeMessage(InboxMessage message);

    /**
     * Writes {@code messages} to the inbox.
     */
    void writeMessages(ShardIndex shard, Iterable<InboxMessage> messages);

    /**
     * Removes a {@code message} from the inbox.
     */
    void removeMessage(InboxMessage message);

    /**
     * Removes {@code messages} from the inbox.
     */
    void removeMessages(ShardIndex shard, Iterable<InboxMessage> messages);
}
