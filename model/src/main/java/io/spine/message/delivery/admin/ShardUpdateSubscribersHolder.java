/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.admin;

import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.spine.logging.Logging;
import io.spine.message.delivery.admin.grpc.ShardInfoUpdate;

import javax.annotation.concurrent.ThreadSafe;
import java.util.ArrayList;
import java.util.Set;

import static com.google.common.collect.Sets.newConcurrentHashSet;

/**
 * Collection of the {@code StreamObserver} for {@code ShardInfoUpdate}.
 *
 * <p>Holds and manges several {@code StreamObserver<ShardInfoUpdate>} in a thread-safe manner.
 *
 * <p>We use {@code newConcurrentHashSet()} to avoid {@code ConcurrentModificationException}
 * in cases when we are iterating over the set to notify subscribers and a new subscriber
 * arrives and the neighbor thread modifies the collection.
 *
 * <p>It's possible that one thread iterates over the set to notify subscribers and
 * one subscriber is being closed at this moment and removed from the collection.
 * In this scenario if we started to iterate over the collection before the subscriber
 * is closed and removed most probably we will get the closed subscriber during the iteration
 * and will try to notify it. But this will not lead to a problem because
 * the {@link #notifySubs(ShardInfoUpdate)} handles invalid subscribers removing them from
 * the collection, which is not a problem also, even if we will try to remove an already
 * removed element.
 */
@ThreadSafe
public class ShardUpdateSubscribersHolder implements Logging {

    private final Set<StreamObserver<ShardInfoUpdate>> subscribers = newConcurrentHashSet();

    public void addSubscriber(StreamObserver<ShardInfoUpdate> subscriber) {
        subscribers.add(subscriber);
        toServerCall(subscriber).setOnCancelHandler(() -> subscribers.remove(subscriber));
        _debug().log("Added one subscriber, current number of subscribers = %d",
                     subscribers.size());
    }

    /**
     * Notifies all existent subscribers about the new {@code ShardInfoChange}.
     *
     * <p>If an error occurs when trying to notify subscriber it is marked as invalid and removed
     * from the subscribers list.
     */
    public void notifySubs(ShardInfoUpdate update) {
        _debug().log("Notifying %d subscribers about update.", subscribers.size());
        var invalidSubs = new ArrayList<StreamObserver<ShardInfoUpdate>>();
        for (var sub : subscribers) {
            try {
                sub.onNext(update);
            } catch (RuntimeException e) {
                _debug().withCause(e)
                        .log("Error notifying the subscriber; it will be removed.");
                invalidSubs.add(sub);
                sub.onError(e);
            }
        }
        invalidSubs.forEach(subscribers::remove);
    }

    /**
     * Casts the given {@code observer} to the {@code ServerCallStreamObserver}.
     *
     * <p>According to the {@link ServerCallStreamObserver} docs it's safe to cast
     * {@code StreamObserver} to {@code ServerCallStreamObserver} in server side implementation
     * of the service.
     */
    private static ServerCallStreamObserver<ShardInfoUpdate>
    toServerCall(StreamObserver<ShardInfoUpdate> observer) {
        return (ServerCallStreamObserver<ShardInfoUpdate>) observer;
    }
}
