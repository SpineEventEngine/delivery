/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import com.google.protobuf.Message;

/**
 * Subscriber that is able to receive updates on each “write” or “delete” operations in some
 * {@link  ReportingRecordStorage}.
 *
 * @param <I>
 *         type of ID stored in the storage.
 * @param <R>
 *         type of records stored in the storage.
 */
public interface UpdateSubscriber<I, R extends Message> {

    /**
     * Handles the notification about the “write” operation that this subscriber is notified about.
     */
    void onWrite(I id, R message);

    /**
     * Handles the notification about the “delete” operation that this subscriber is notified about.
     */
    void onDelete(I id);
}
