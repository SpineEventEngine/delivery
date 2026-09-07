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
 * An observer that allows waiting for a particular message being sent to it.
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
        var future = new FutureWithPredicate<>(predicate);
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
