/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.InboxMessageId;
import io.spine.server.delivery.InboxMessageStatus;
import io.spine.server.delivery.Page;
import io.spine.server.delivery.ShardIndex;

import java.util.Optional;

/**
 * Queries the remote Inbox state.
 */
public interface InboxReader {

    /**
     * Tries to find an {@code InboxMessage} with a particular {@code messageId}.
     *
     * @return the message found or {@code Optional.empty()} if there is no message with
     *         the specified ID
     */
    Optional<InboxMessage> find(InboxMessageId messageId);

    /**
     * Reads the contents of the storage by the given shard index and returns the first page
     * of the results.
     *
     * <p>The older items go first.
     *
     * @param shard
     *         the shard index to return the results for
     * @param pageSize
     *         the maximum number of the elements per page
     * @return the first page of the results
     */
    Page<InboxMessage> readAll(ShardIndex shard, int pageSize);

    /**
     * Finds the newest message {@linkplain InboxMessageStatus#TO_DELIVER to deliver}
     * in the given shard.
     *
     * @param shard
     *         the shard index to look in
     * @return the message found or {@code Optional.empty()} if there are no messages to deliver
     *         in the specified shard
     */
    Optional<InboxMessage> newestMessageToDeliver(ShardIndex shard);
}
