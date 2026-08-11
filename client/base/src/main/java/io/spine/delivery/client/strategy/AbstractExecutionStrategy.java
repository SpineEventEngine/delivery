/*
 * Copyright (c) 2000-2026 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.delivery.client.strategy;

import com.google.common.collect.ImmutableList;
import io.spine.delivery.client.ExecutionFailedException;
import io.spine.delivery.client.RequestExecutionStrategy;
import io.spine.delivery.client.RequestWithResult;
import io.spine.delivery.client.VoidRequest;

import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A basic implementation of the {@code RequestExecutionStrategy} that executes requests
 * and delegates error handling, if any, to subclasses.
 */
public abstract class AbstractExecutionStrategy implements RequestExecutionStrategy {

    @Override
    public final void execute(VoidRequest request) throws ExecutionFailedException {
        tryExecute(request, ImmutableList.of());
    }

    @Override
    public final <R> R evaluate(RequestWithResult<R> request) throws ExecutionFailedException {
        return tryEvaluate(request, ImmutableList.of());
    }

    /**
     * Tries to execute the {@code request} and handles an occurred exception
     * using {@link #handleException(FailedVoidRequest)} if any.
     */
    private void tryExecute(VoidRequest request, ImmutableList<RuntimeException> previous) {
        checkNotNull(request);
        try {
            request.run();
        } catch (RuntimeException e) {
            var allExceptions = append(previous, e);
            Runnable retry = () -> tryExecute(request, allExceptions);
            handleException(new FailedVoidRequest(retry, allExceptions)).execute();
        }
    }

    /**
     * Tries to evaluate the {@code request} and handles an occurred exception
     * using {@link #handleException(FailedRequest)} if any.
     */
    private <R> R
    tryEvaluate(RequestWithResult<R> request, ImmutableList<RuntimeException> previous) {
        checkNotNull(request);
        try {
            return request.evaluate();
        } catch (RuntimeException e) {
            var allExceptions = append(previous, e);
            Supplier<R> retry = () -> tryEvaluate(request, allExceptions);
            return handleException(new FailedRequest<>(retry, allExceptions)).execute();
        }
    }

    /**
     * Handles exceptions occurred during {@code RequestWithResult} execution and returns
     * an {@code ActionWithResult} that tells what to do next after the failure.
     */
    protected abstract <R> ActionWithResult<R> handleException(FailedRequest<R> failure);

    /**
     * Handles exceptions occurred during {@code VoidRequest} execution and returns
     * an {@code Action} that tells what to do next after the failure.
     */
    protected abstract Action handleException(FailedVoidRequest failure);

    /**
     * Returns a list of the given {@code elements} with the appended {@code element}.
     */
    private static <T> ImmutableList<T> append(Iterable<T> elements, T element) {
        return ImmutableList.<T>builder()
                .addAll(elements)
                .add(element)
                .build();
    }
}
