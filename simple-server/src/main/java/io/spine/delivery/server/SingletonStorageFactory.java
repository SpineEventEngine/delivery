/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.server;

import com.google.protobuf.Message;
import io.spine.server.ContextSpec;
import io.spine.server.storage.AbstractStorage;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.RecordStorage;
import io.spine.server.storage.StorageFactory;
import io.spine.server.storage.StorageGroup;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Factory that preserves created storages for further usages and doesn't create a new storage for
 * every request.
 */
public final class SingletonStorageFactory implements StorageFactory {

    private final StorageFactory delegate;

    /**
     * The storages created so far.
     *
     * <p>Kept in a concurrent map: a storage may be created lazily on a delivery
     * worker thread; see {@code StorageFactory.createEntityStateHistoryStorage}.
     */
    private final Map<Key, RecordStorage<?, ?>> map = new ConcurrentHashMap<>();

    /**
     * Creates a new {@code SingletonStorageFactory} with the given {@code delegate}.
     *
     * @param delegate
     *         factory that will be used to create storage in case the requested storage
     *         has never been created yet.
     */
    public SingletonStorageFactory(StorageFactory delegate) {
        checkNotNull(delegate);
        this.delegate = delegate;
    }

    /**
     * Returns already created {@code RecordStorage} or delegates creation to the delegate if
     * there is no storage created for the given {@code context}, {@code spec}, and {@code group}.
     */
    @SuppressWarnings("unchecked")
    @Override
    public <I, R extends Message> RecordStorage<I, R>
    createRecordStorage(ContextSpec context,
                        RecordSpec<I, R> spec,
                        @Nullable StorageGroup group) {
        Key key = Key.of(context, spec, group);
        return (RecordStorage<I, R>)
                map.computeIfAbsent(key,
                                    (k) -> delegate.createRecordStorage(context, spec, group));
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public void close() {
        map.values().forEach(AbstractStorage::close);
        delegate.close();
    }

    /**
     * A hash map key based on {@link ContextSpec}, {@link RecordSpec},
     * and {@link StorageGroup} objects.
     *
     * <p>The {@code StorageGroup} takes part in the key so that a grouped storage —
     * a per-entity history — is never conflated with the storage of the latest states,
     * which shares the record specification.
     */
    private static class Key {

        private final ContextSpec context;
        private final RecordSpec<?, ?> spec;
        private final @Nullable StorageGroup group;

        private Key(ContextSpec context, RecordSpec<?, ?> spec, @Nullable StorageGroup group) {
            this.context = context;
            this.spec = spec;
            this.group = group;
        }

        /**
         * Creates a new {@code Key} with provided {@code context}, {@code spec},
         * and {@code group}.
         */
        public static Key of(ContextSpec context,
                             RecordSpec<?, ?> spec,
                             @Nullable StorageGroup group) {
            checkNotNull(context);
            checkNotNull(spec);
            return new Key(context, spec, group);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key)) {
                return false;
            }
            Key toCompare = (Key) o;
            return context.equals(toCompare.context)
                    && spec.idType().equals(toCompare.spec.idType())
                    && spec.sourceType().equals(toCompare.spec.sourceType())
                    && spec.recordType().equals(toCompare.spec.recordType())
                    && Objects.equals(group, toCompare.group);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    context, spec.idType(), spec.sourceType(), spec.recordType(), group);
        }
    }
}
