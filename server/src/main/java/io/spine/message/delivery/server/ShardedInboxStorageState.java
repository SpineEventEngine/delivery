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
import io.spine.server.projection.Projection;

import java.util.stream.Collectors;

//TODO:2021-06-16:yuri-sergiichuk: add docs.
final class ShardedInboxStorageState
        extends Projection<ShardedStorageId, ShardedInboxStorage, ShardedInboxStorage.Builder> {

    //TODO:2021-06-16:yuri-sergiichuk: add repo and routing.

    //TODO:2021-06-16:yuri-sergiichuk: add tests.
    @Subscribe
    void on(MessageWritten e, EventContext context) {
        var sortedMessages = builder()
                .addMessage(e.getMessage())
                .getMessageList()
                .stream()
                .sorted(InboxMessageComparator.chronologically)
                .collect(Collectors.toList());
        builder().clearMessage()
                 .addAllMessage(sortedMessages);
    }
}
