/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.InboxMessageId;
import io.spine.server.delivery.InboxStorage;
import io.spine.server.storage.StorageFactory;

/**
 * Extends the {@link InboxStorage} by exposing some of API endpoints into {@code public}.
 */
public class ExtendedInboxStorage extends InboxStorage {

    public ExtendedInboxStorage(StorageFactory factory, boolean multitenant) {
        super(factory, multitenant);
    }

    @Override
    public synchronized void writeBatch(Iterable<InboxMessage> messages) {
        super.writeBatch(messages);
    }

    @CanIgnoreReturnValue
    @Override
    public boolean delete(InboxMessageId id) {
        return super.delete(id);
    }

    @Override
    public void deleteAll(Iterable<InboxMessageId> ids) {
        super.deleteAll(ids);
    }
}
