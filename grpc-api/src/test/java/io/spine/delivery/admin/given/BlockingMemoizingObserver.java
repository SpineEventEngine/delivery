/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.admin.given;

import com.google.common.util.concurrent.AbstractFuture;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.grpc.MemoizingObserver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Predicate;

import static java.util.Collections.synchronizedList;

/**
 * An observer that allows to wait for a particular messages being sent to it.
 *
 * @param <T>
 *         type of the observer values
 */
public final class BlockingMemoizingObserver<T> extends MemoizingObserver<T> {

    private final List<FutureWithPredicate<T>> onNextFutures =
            synchronizedList(new ArrayList<>());
    private final List<FutureWithPredicate<Throwable>> onErrorFutures =
            synchronizedList(new ArrayList<>());

    @Override
    public void onNext(T value) {
        super.onNext(value);
        onNextFutures.removeIf(future -> future.set(value));
    }

    @Override
    public void onError(Throwable t) {
        super.onError(t);
        onErrorFutures.removeIf(future -> future.set(t));
    }

    /**
     * Returns a {@code Future} that will be resolved as soon as the next value is delivered
     * to the observer.
     */
    public Future<T> waitForAny() {
        return waitForMatching(o -> true);
    }

    /**
     * Returns a {@code Future} that will be resolved as soon as the next value matching the given
     * {@code predicate} is delivered to the observer.
     */
    public Future<T> waitForMatching(Predicate<T> predicate) {
        FutureWithPredicate<T> future = new FutureWithPredicate<>(predicate);
        onNextFutures.add(future);
        return future;
    }

    /**
     * Future that can be resolved only with a value matching a predicate.
     *
     * @param <T>
     *         a type of the value the {@code Future} provides
     */
    private static final class FutureWithPredicate<T> extends AbstractFuture<T> {

        private final Predicate<T> predicate;

        private FutureWithPredicate(Predicate<T> predicate) {
            super();
            this.predicate = predicate;
        }

        /**
         * Resolves the {@code Future} with the given {@code value}.
         *
         * @param value
         *         the value to be used as the result
         * @return true if the given {@code value} matches the predicate and can be set
         *         to the {@code Future}
         * @throws IllegalStateException
         *         if the given {@code value} matches the predicate, but it cannot be set because
         *         it's already been set
         */
        @CanIgnoreReturnValue
        @Override
        protected boolean set(T value) throws IllegalStateException {
            if (predicate.test(value)) {
                if (!super.set(value)) {
                    throw new IllegalStateException(
                            "Cannot resolve the `Future` as it's already has been resolved.");
                }
                return true;
            }
            return false;
        }
    }
}
