/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.admin;

import io.grpc.stub.ServerCallStreamObserver;
import io.spine.logging.Logging;
import io.spine.message.delivery.admin.grpc.ShardInfoUpdate;

import javax.annotation.concurrent.ThreadSafe;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collection of the {@code StreamObserver} for {@code ShardInfoUpdate}.
 *
 * <p>Holds and manges several {@code StreamObserver<ShardInfoUpdate>} in a thread-safe manner.
 *
 * <p>We use {@code ConcurrentHashMap} to avoid {@code ConcurrentModificationException}
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
public final class ShardUpdateSubscribersHolder implements Logging {

    private final ConcurrentHashMap<String, ServerCallStreamObserver<ShardInfoUpdate>> subscribers =
            new ConcurrentHashMap<>();

    public void addSubscriber(ServerCallStreamObserver<ShardInfoUpdate> subscriber) {
        String uuid = UUID.randomUUID()
                          .toString();
        subscribers.put(uuid, subscriber);
        _debug().log("Added new subscriber [%s], current number of subscribers = %d",
                     uuid, subscribers.size());
        subscriber.setOnCancelHandler(() -> {
            _debug().log("Subscriber [%s] closed. Removing...", uuid);
            removeVerbose(uuid);
        });
    }

    /**
     * Notifies all existent subscribers about the new {@code ShardInfoChange}.
     *
     * <p>If an error occurs when trying to notify subscriber it is marked as invalid and removed
     * from the subscribers list.
     */
    public void notifySubs(ShardInfoUpdate update) {
        _debug().log("Notifying %d subscribers about update.", subscribers.size());
        var invalidSubIds = new ArrayList<String>();
        for (var entry : subscribers.entrySet()) {
            try {
                entry.getValue()
                     .onNext(update);
            } catch (RuntimeException e) {
                _debug().withCause(e)
                        .log("Error notifying the subscriber [%s]; it will be removed.",
                             entry.getKey());
                invalidSubIds.add(entry.getKey());
                entry.getValue()
                     .onError(e);
            }
        }
        invalidSubIds.forEach(this::removeVerbose);
    }

    /**
     * Removes a subscriber with the given {@code uuid} and logs the result of the removal.
     */
    private void removeVerbose(String uuid) {
        Optional.ofNullable(subscribers.remove(uuid))
                .ifPresentOrElse(
                        (r) -> _debug().log("Subscriber [%s] removed.", uuid),
                        () -> _debug().log("Subscriber [%s] not found.", uuid)
                );
    }
}
