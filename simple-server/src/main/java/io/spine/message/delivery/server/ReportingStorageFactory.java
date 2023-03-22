/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.server;

import com.google.protobuf.Message;
import io.spine.server.ContextSpec;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.StorageFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Sets.newConcurrentHashSet;
import static java.util.UUID.randomUUID;

/**
 * Storage factory that allows subscribing to update operations of its storages.
 */
public final class ReportingStorageFactory implements StorageFactory {

    private final StorageFactory delegate;

    /**
     * The field preserves all storages that is created by this factory.
     *
     * <p>This is done to add new subscriptions to already created storages.
     */
    private final Map<TypeSpec<?, ?>, Set<ReportingRecordStorage<?, ?>>> storages = new ConcurrentHashMap<>();

    /**
     * The field preserves subscriptions that is passed to the factory.
     *
     * <p>This is done to add already existent subscriptions to a newly created storages.
     */
    private final Map<String, ComplexSubscription<?, ?>> subscriptions = new ConcurrentHashMap<>();

    public ReportingStorageFactory(StorageFactory delegate) {
        this.delegate = delegate;
    }

    @Override
    public <I, R extends Message> ReportingRecordStorage<I, R>
    createRecordStorage(ContextSpec context, RecordSpec<I, R, ?> spec) {
        var storage = delegate.createRecordStorage(context, spec);
        var reportingStorage = new ReportingRecordStorage<>(context, storage);
        TypeSpec<I, R> typeSpec = TypeSpec.of(spec);
        remember(typeSpec, reportingStorage);
        addExistentSubscribers(typeSpec, reportingStorage);
        return reportingStorage;
    }

    /**
     * Adds existent subscriptions to the given {@code storage}.
     */
    @SuppressWarnings("unchecked") // We ensure types using `key`.
    private <I, R extends Message> void
    addExistentSubscribers(TypeSpec<I, R> typeSpec, ReportingRecordStorage<I, R> storage) {
        subscriptions
                .values()
                .stream()
                .filter(s -> typeSpec.equals(s.typeSpec()))
                .map(s -> (ComplexSubscription<I, R>) s)
                .forEach(s -> s.addSubscription(storage.subscribe(s.subscriber())));
    }

    @Override
    public void close() throws Exception {
        delegate.close();
    }

    /**
     * Adds the given {@code subscriber} to all storages that have the given {@code idType} and
     * {@code recordType}.
     *
     * <p>This also includes storages that will be created in the future. The method preserves
     * the given subscription and adds it to storages of the given types that will be created in the
     * future.
     */
    public <I, R extends Message> StorageSubscription
    subscribe(Class<I> idType, Class<R> recordType, StorageSubscriber<I, R> subscriber) {
        TypeSpec<I, R> typeSpec = new TypeSpec<>(idType, recordType);
        var subscriptions = subscribeOnExistentStorages(typeSpec, subscriber);
        return remember(new ComplexSubscription<>(typeSpec, subscriber, subscriptions));
    }

    /**
     * Adds the given {@code subscriber} to already created storages with the given {@code spec}.
     */
    @SuppressWarnings("unchecked") // We ensure types using `typeSpec`.
    private <I, R extends Message> Set<StorageSubscription>
    subscribeOnExistentStorages(TypeSpec<I, R> spec, StorageSubscriber<I, R> subscriber) {
        Set<StorageSubscription> subscriptions = new HashSet<>();
        storages(spec).forEach(storage -> {
            ReportingRecordStorage<I, R> typedStorage = (ReportingRecordStorage<I, R>) storage;
            subscriptions.add(typedStorage.subscribe(subscriber));
        });
        return subscriptions;
    }

    /**
     * Saves the given {@code subscription} and returns such {@code UpdateSubscription} that will
     * remove the subscription if canceled.
     */
    private <I, R extends Message> StorageSubscription
    remember(ComplexSubscription<I, R> subscription) {
        String id = randomUUID().toString();
        this.subscriptions.put(id, subscription);
        StorageSubscription storageSubscription = () -> Optional
                .ofNullable(this.subscriptions.remove(id))
                .ifPresent(ComplexSubscription::unsubscribeAll);
        return storageSubscription;
    }

    /**
     * Remembers the given {@code storage} associating it with the given {@code typeSpec}.
     */
    private <I, R extends Message> void
    remember(TypeSpec<I, R> typeSpec, ReportingRecordStorage<I, R> storage) {
        storages(typeSpec).add(storage);
    }

    /**
     * Returns a {@code Set} of storages of the given {@code typeSpec}.
     *
     * <p>If there is no storages for the given types returns an empty {@code Set}.
     */
    private Set<ReportingRecordStorage<?, ?>> storages(TypeSpec<?, ?> typeSpec) {
        return storages.computeIfAbsent(typeSpec, k -> newConcurrentHashSet());
    }

    /**
     * Subscription for multiple storages of the same type.
     *
     * @param <I>
     *         the type of the record identifiers
     * @param <R>
     *         the type of the message records
     */
    private static final class ComplexSubscription<I, R extends Message> {

        private final TypeSpec<I, R> typeSpec;

        private final StorageSubscriber<I, R> subscriber;

        private final Set<StorageSubscription> subscriptions = newConcurrentHashSet();

        /**
         * Creates a new {@code ComplexSubscription} with the given {@code typeSpec},
         * {@code subscriber}, and {@code subscriptions}.
         */
        private ComplexSubscription(TypeSpec<I, R> typeSpec,
                                    StorageSubscriber<I, R> subscriber,
                                    Set<StorageSubscription> subscriptions) {
            this.typeSpec = typeSpec;
            this.subscriber = subscriber;
            this.subscriptions.addAll(subscriptions);
        }

        /**
         * Returns a {@code TypeSpec} of this subscription.
         */
        public TypeSpec<I, R> typeSpec() {
            return typeSpec;
        }

        /**
         * Returns {@code UpdateSubscriber} associated with this subscription.
         */
        public StorageSubscriber<I, R> subscriber() {
            return subscriber;
        }

        /**
         * Cancels all underlying subscriptions.
         */
        public void unsubscribeAll() {
            subscriptions.forEach(StorageSubscription::cancel);
        }

        /**
         * Adds a new {@code subscription} to this complex subscription.
         */
        public void addSubscription(StorageSubscription subscription) {
            checkNotNull(subscription);
            subscriptions.add(subscription);
        }
    }

    /**
     * Specification of the storage based around types of stored IDs and records.
     *
     * <p>This specification is a simplified version of {@link RecordSpec} but this one indeed
     * implements {@code equals()} and {@code hashCode()} that makes it appropriate to use as a key
     * for maps.
     *
     * @param <I>
     *         type of stored ID
     * @param <R>
     *         type of stored records
     */
    private static final class TypeSpec<I, R extends Message> {

        private final Class<I> idType;
        private final Class<R> recordType;

        /**
         * Creates new {@code TypeSpec} of the given {@code RecordSpec}.
         */
        public static <I, R extends Message> TypeSpec<I, R> of(RecordSpec<I, R, ?> spec) {
            return new TypeSpec<>(spec.idType(), spec.storedType());
        }

        /**
         * Creates new {@code TypeSpec} with the given {@code idType} and {@code recordType}.
         */
        private TypeSpec(Class<I> idType, Class<R> recordType) {
            this.idType = checkNotNull(idType);
            this.recordType = checkNotNull(recordType);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof TypeSpec)) {
                return false;
            }
            TypeSpec<?, ?> typeSpec = (TypeSpec<?, ?>) o;
            if (!idType.equals(typeSpec.idType)) {
                return false;
            }
            return recordType.equals(typeSpec.recordType);
        }

        @Override
        public int hashCode() {
            int result = idType.hashCode();
            result = 31 * result + recordType.hashCode();
            return result;
        }
    }
}
