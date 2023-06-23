/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.server.storage.hazelcast;

import com.google.protobuf.Message;
import com.hazelcast.core.HazelcastInstance;
import io.spine.logging.Logging;
import io.spine.server.ContextSpec;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.RecordStorage;
import io.spine.server.storage.StorageFactory;

import static com.hazelcast.core.Hazelcast.newHazelcastInstance;

public class HazelcastStorageFactory implements StorageFactory, Logging {

    private final HazelcastInstance hazelcast = newHazelcastInstance();

    public static HazelcastStorageFactory newInstance() {
        return new HazelcastStorageFactory();
    }

    @Override
    public <I, R extends Message> HazelcastRecordStorage<I, R>
    createRecordStorage(ContextSpec context, RecordSpec<I, R, ?> recordSpec) {
        return new HazelcastRecordStorage<>(context, recordSpec, hazelcast);
    }

    @Override
    public void close() {
        hazelcast.shutdown();
    }
}
