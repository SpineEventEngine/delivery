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

import io.spine.server.ContextSpec;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.ShardSessionRecord;
import io.spine.server.storage.DelegatingRecordStorage;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.StorageFactory;

import java.util.Iterator;

import static io.spine.server.ContextSpec.singleTenant;

/**
 * A storage of {@linkplain ShardSessionRecord shard sessions}.
 *
 * <p>Delegates interactions with the storage implementation to a storage created by
 * the configured {@link StorageFactory}.
 */
public final class ShardRegistryStorage
        extends DelegatingRecordStorage<ShardIndex, ShardSessionRecord> {

    private ShardRegistryStorage(ContextSpec contextSpec, StorageFactory factory) {
        super(contextSpec, factory.createRecordStorage(contextSpec, spec()));
    }

    /**
     * Creates a new storage backed by the supplied {@code factory}.
     */
    public ShardRegistryStorage(StorageFactory factory) {
        this(singleTenant("ShardRegistry"), factory);
    }

    @Override
    public Iterator<ShardSessionRecord> readAll() {
        return super.readAll();
    }

    private static RecordSpec<ShardIndex, ShardSessionRecord> spec() {
        @SuppressWarnings("ConstantConditions" /* Protobuf getters do not return {@code null}s. */)
        var spec = new RecordSpec<>(ShardIndex.class,
                                    ShardSessionRecord.class,
                                    ShardSessionRecord::getIndex);
        return spec;
    }
}
