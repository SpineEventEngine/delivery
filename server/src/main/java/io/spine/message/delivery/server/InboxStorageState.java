/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import io.spine.core.EventContext;
import io.spine.core.Subscribe;
import io.spine.message.delivery.server.event.MessageWritten;
import io.spine.server.delivery.InboxId;
import io.spine.server.projection.Projection;

/**
 * Holds state of the {@link InboxStorage}.
 */
final class InboxStorageState extends Projection<InboxId, InboxStorage, InboxStorage.Builder> {

    @Subscribe
    void on(MessageWritten e, EventContext context) {
        builder().addMessage(e.getMessage());
    }
}
