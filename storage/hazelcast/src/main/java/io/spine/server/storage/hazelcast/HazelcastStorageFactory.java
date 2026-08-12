/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.server.storage.hazelcast;

import com.google.protobuf.Message;
import com.hazelcast.core.HazelcastInstance;
import io.spine.logging.WithLogging;
import io.spine.server.ContextSpec;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.StorageFactory;
import io.spine.server.storage.StorageGroup;
import org.jspecify.annotations.Nullable;

import static com.hazelcast.core.Hazelcast.newHazelcastInstance;

/**
 * A factory for Hazelcast-based storages.
 *
 * <p>To get more info about what is Hazelcast in general please refer to the
 * <a href="https://hazelcast.com/">Hazelcast</a></p> official website.
 *
 * <p>The main feature of the storages produced by this factory is replication support. When the
 * factory instance is obtained, a new embedded Hazelcast server is started. The server allows
 * discovering other servers running in the same network. Servers will automatically form a cluster
 * where each server is storing a copy of all the cluster data. This means that records stored on
 * one instance will be available for read and modification for all the Delivery instances
 * in the same network.
 *
 * <p>Pay attention that each new factory instance creation will run a new Hazelcast server.
 */
public final class HazelcastStorageFactory implements StorageFactory, WithLogging {

    private final HazelcastInstance hazelcast = newHazelcastInstance();

    /**
     * Creates a new {@code HazelcastStorageFactory} and starts a new
     * {@linkplain HazelcastInstance}.
     */
    public static HazelcastStorageFactory newInstance() {
        return new HazelcastStorageFactory();
    }

    /**
     * {@inheritDoc}
     *
     * <p>A storage belonging to a {@linkplain StorageGroup group} — a per-entity
     * history — is allocated a distinct Hazelcast map, its name composed of the group
     * name and the simple name of the record type. Storages outside any group are
     * named after the {@linkplain RecordSpec#sourceType() source type} of the record
     * specification. See {@link HazelcastRecordStorage} for the naming details.
     */
    @Override
    public <I, R extends Message> HazelcastRecordStorage<I, R>
    createRecordStorage(ContextSpec context,
                        RecordSpec<I, R> recordSpec,
                        @Nullable StorageGroup group) {
        return new HazelcastRecordStorage<>(context, recordSpec, group, hazelcast);
    }

    @Override
    public boolean isOpen() {
        return hazelcast.getLifecycleService()
                        .isRunning();
    }

    @Override
    public void close() {
        hazelcast.shutdown();
    }
}
