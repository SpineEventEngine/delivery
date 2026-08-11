/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.server.given;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Message;
import io.spine.delivery.server.StorageSubscriber;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A subscriber that memoizes all write and delete updates sent to it.
 */
public class MemoizingStorageSubscriber<I, R extends Message> implements StorageSubscriber<I, R> {

    private final List<SingleWrite<I, R>> writes = new ArrayList<>();

    private final List<I> deletions = new ArrayList<>();

    @Override
    public void onWrite(I id, R message) {
        writes.add(SingleWrite.of(id, message));
    }

    @Override
    public void onDelete(I id) {
        deletions.add(id);
    }

    /**
     * Returns all write updates sent to this subscriber.
     */
    public ImmutableList<SingleWrite<I, R>> writes() {
        return ImmutableList.copyOf(writes);
    }

    /**
     * Returns all delete updates sent to this subscriber.
     */
    public ImmutableList<I> deletions() {
        return ImmutableList.copyOf(deletions);
    }

    /**
     * Represents a single write operation that the {@code MemoizingUpdateSubscriber} has been
     * notified about.
     */
    public static class SingleWrite<I, R extends Message> {

        private final I id;
        private final R message;

        /**
         * Creates a new {@code SingleWrite} with the given {@code id} and {@code record}.
         */
        public static <I, R extends Message> SingleWrite<I, R> of(I id, R record) {
            checkNotNull(id);
            checkNotNull(record);
            return new SingleWrite<>(id, record);
        }

        private SingleWrite(I id, R message) {
            this.id = id;
            this.message = message;
        }

        /**
         * Returns an ID of the written record.
         */
        public I id() {
            return id;
        }

        /**
         * Returns a written message.
         */
        public R message() {
            return message;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof SingleWrite<?, ?> write)) {
                return false;
            }
            return id.equals(write.id) && message.equals(write.message);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, message);
        }
    }
}
