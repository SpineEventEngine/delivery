/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.spine.delivery.client;

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
