/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import io.spine.core.EventContext;
import io.spine.core.Subscribe;
import io.spine.message.delivery.server.event.MessageWritten;
import io.spine.server.delivery.InboxMessageComparator;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.projection.Projection;

import java.util.stream.Collectors;

import static io.spine.server.delivery.InboxMessageComparator.chronologically;

/**
 * Holds state of all inbox messages which belong to a particular {@linkplain ShardIndex shard}.
 *
 * <p>The messages are stored {@linkplain InboxMessageComparator#chronologically chronologically}.
 */
final class ShardedInboxStorage
        extends Projection<ShardIndex, MessagesInShard, MessagesInShard.Builder> {

    @Subscribe
    void on(MessageWritten e, EventContext context) {
        var sortedMessages = builder()
                .addMessage(e.getMessage())
                .getMessageList()
                .stream()
                .sorted(chronologically)
                .collect(Collectors.toList());
        builder().clearMessage()
                 .addAllMessage(sortedMessages);
    }
}
