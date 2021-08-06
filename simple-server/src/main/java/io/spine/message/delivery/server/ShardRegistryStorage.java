/*
 * Copyright (c) 2000-2021 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import io.spine.server.ContextSpec;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.ShardSessionRecord;
import io.spine.server.storage.MessageRecordSpec;
import io.spine.server.storage.RecordStorageDelegate;
import io.spine.server.storage.StorageFactory;

import java.util.Iterator;

import static io.spine.server.ContextSpec.singleTenant;

/**
 * A storage of {@linkplain ShardSessionRecord shard sessions}.
 *
 * <p>Delegates interactions with the storage implementation to a storage created by
 * the configured {@link StorageFactory}.
 */
final class ShardRegistryStorage extends RecordStorageDelegate<ShardIndex, ShardSessionRecord> {

    private ShardRegistryStorage(ContextSpec contextSpec, StorageFactory factory) {
        super(contextSpec, factory.createRecordStorage(contextSpec, spec()));
    }

    /**
     * Creates a new storage backed by the supplied {@code factory}.
     */
    ShardRegistryStorage(StorageFactory factory) {
        this(singleTenant("ShardRegistry"), factory);
    }

    @Override
    public Iterator<ShardSessionRecord> readAll() {
        return super.readAll();
    }

    private static MessageRecordSpec<ShardIndex, ShardSessionRecord> spec() {
        @SuppressWarnings("ConstantConditions" /* Protobuf getters do not return {@code null}s. */)
        MessageRecordSpec<ShardIndex, ShardSessionRecord> spec =
                new MessageRecordSpec<>(ShardIndex.class,
                                        ShardSessionRecord.class,
                                        ShardSessionRecord::getIndex);
        return spec;
    }
}
