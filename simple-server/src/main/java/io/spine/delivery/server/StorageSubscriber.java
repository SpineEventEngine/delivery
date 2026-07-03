/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.server;

import com.google.protobuf.Message;

/**
 * Receives updates on each “write” or “delete” operation in a storage.
 *
 * @see ReportingRecordStorage
 * @see ReportingStorageFactory
 *
 * @param <I>
 *         type of record ID stored in the storage this subscription belongs to
 * @param <R>
 *         type of records stored in the storage this subscription belongs to
 */
public interface StorageSubscriber<I, R extends Message> {

    /**
     * Handles the notification about the “write” operation that this subscriber is notified about.
     *
     * @param id
     *         ID of the written record
     * @param message
     *         written message
     */
    void onWrite(I id, R message);

    /**
     * Handles the notification about the “delete” operation that this subscriber is notified about.
     *
     * @param id
     *         ID of the deleted message
     */
    void onDelete(I id);
}
