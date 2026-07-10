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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Factory that preserves created storages for further usages and doesn't create a new storage for
 * every request.
 */
public final class SingletonStorageFactory implements StorageFactory {

    private final StorageFactory delegate;

    private final Map<Key, RecordStorage<?, ?>> map = new HashMap<>();

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
     * there is no storage created for the given {@code context} and {@code spec}.
     */
    @SuppressWarnings("unchecked")
    @Override
    public <I, R extends Message> RecordStorage<I, R>
    createRecordStorage(ContextSpec context, RecordSpec<I, R> spec) {
        Key key = Key.of(context, spec);
        return (RecordStorage<I, R>)
                map.computeIfAbsent(key, (k) -> delegate.createRecordStorage(context, spec));
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
     * A hash map key based on {@link ContextSpec} and {@link RecordSpec} objects.
     */
    private static class Key {

        private final ContextSpec context;
        private final RecordSpec<?, ?> spec;

        private Key(ContextSpec context, RecordSpec<?, ?> spec) {
            this.context = context;
            this.spec = spec;
        }

        /**
         * Creates a new {@code Key} with provided {@code context} and {@code spec}.
         */
        public static Key of(ContextSpec context, RecordSpec<?, ?> spec) {
            checkNotNull(context);
            checkNotNull(spec);
            return new Key(context, spec);
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
            Class<?> idType = spec.idType();
            Class<?> storedType = spec.recordType();
            return context.equals(toCompare.context)
                    && idType.equals(toCompare.spec.idType())
                    && storedType.equals(toCompare.spec.recordType());
        }

        @Override
        public int hashCode() {
            return Objects.hash(context, spec.idType(), spec.recordType());
        }
    }
}
