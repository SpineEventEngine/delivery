/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.server;

/**
 * Acknowledgement of the successful subscription.
 *
 * @see StorageSubscriber
 * @see ReportingRecordStorage
 * @see ReportingStorageFactory
 */
public interface StorageSubscription {

    /**
     * Cancels this subscription.
     */
    void cancel();
}
