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
 * <a href="https://hazelcast.com/">Hazelcast</a> official website.
 *
 * <p>The main feature of the storages produced by this factory is replication support. When the
 * factory instance is obtained, a new embedded Hazelcast member is started. Members discover
 * each other by IP multicast under the cluster name {@code delivery}, so Delivery servers
 * running in one network that carries multicast form a cluster with no further configuration.
 * Each member stores a copy of the cluster data, so records stored on one instance are
 * available for reading and modification on all the Delivery servers in the cluster.
 *
 * <p>The member is configured by the {@code hazelcast.yaml} resource of this module, which
 * replaces the defaults bundled with Hazelcast. Cloud auto-detection is switched off there,
 * because on an Azure, GCP, AWS, or Kubernetes host it would take precedence over multicast
 * and, lacking credentials, start the member standalone. Any setting can still be overridden
 * the standard Hazelcast way: {@code HZ_*} environment variables, {@code hz.*} system
 * properties, or a complete configuration file passed via {@code -Dhazelcast.config}.
 *
 * <p>Pay attention that each new factory instance creation will run a new Hazelcast member.
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
