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
     * Handles the notification about the “write” operation that this subscriber
     * is notified about.
     *
     * @param id
     *         ID of the written record
     * @param message
     *         written message
     */
    void onWrite(I id, R message);

    /**
     * Handles the notification about the “delete” operation that this subscriber
     * is notified about.
     *
     * @param id
     *         ID of the deleted message
     */
    void onDelete(I id);
}
