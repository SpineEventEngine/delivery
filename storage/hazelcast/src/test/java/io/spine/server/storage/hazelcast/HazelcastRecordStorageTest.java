/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.server.storage.hazelcast;

import io.spine.server.ContextSpec;
import io.spine.server.storage.StorageFactory;
import io.spine.server.storage.StorageTestBase;
import org.junit.jupiter.api.DisplayName;

@DisplayName("`HazelcastRecordStorage` should")
final class HazelcastRecordStorageTest extends StorageTestBase {

    private static final ContextSpec context = ContextSpec
            .singleTenant("HazelcastRecordStorageTest");

    @Override
    protected StorageFactory storageFactory() {
        return HazelcastStorageFactory.newInstance();
    }

    @Override
    protected ContextSpec context() {
        return context;
    }
}
