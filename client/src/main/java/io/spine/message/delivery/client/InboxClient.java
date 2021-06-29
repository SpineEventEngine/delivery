/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import io.spine.message.delivery.event.MessageRemoved;
import io.spine.message.delivery.event.MessageWritten;
import io.spine.message.delivery.event.MessagesRemoved;
import io.spine.message.delivery.event.MessagesWritten;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.ShardIndex;

import java.util.Optional;

/**
 * A client for working with the inbox.
 */
public interface InboxClient {

    /**
     * Tries to write a new {@code message} to the inbox.
     */
    Optional<MessageWritten> writeMessage(InboxMessage message);

    /**
     * Tries to write {@code messages} to the inbox.
     */
    Optional<MessagesWritten> writeMessages(ShardIndex shard, Iterable<InboxMessage> messages);

    /**
     * Tries to remove a {@code message} from the inbox.
     */
    Optional<MessageRemoved> removeMessage(InboxMessage message);

    /**
     * Tries to remove {@code messages} from the inbox.
     */
    Optional<MessagesRemoved> removeMessages(ShardIndex shard, Iterable<InboxMessage> messages);
}
