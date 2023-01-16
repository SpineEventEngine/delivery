/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client.strategy;

import com.google.common.collect.ImmutableList;
import io.spine.message.delivery.client.ExecutionFailedException;
import io.spine.message.delivery.client.RequestExecutionStrategy;
import io.spine.message.delivery.client.RequestWithResult;
import io.spine.message.delivery.client.VoidRequest;

import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Basic implementation of the {@code RequestExecutionStrategy} that executes requests and delegates
 * error handling, if any, to subclasses.
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
     * Tries to execute the {@code request} and handles occurred exception
     * using {@link #handleException(FailureReport)} if any.
     */
    private void tryExecute(VoidRequest request, ImmutableList<RuntimeException> previous) {
        checkNotNull(request);
        try {
            request.run();
        } catch (RuntimeException e) {
            Runnable retry = () -> tryExecute(request, append(previous, e));
            handleException(new FailureReport(retry, previous, e)).execute();
        }
    }

    /**
     * Tries to evaluate the {@code request} and handles occurred exception
     * using {@link #handleException(FailureReportForNonVoidRequest)} if any.
     */
    private <R> R
    tryEvaluate(RequestWithResult<R> request, ImmutableList<RuntimeException> previous) {
        checkNotNull(request);
        try {
            return request.evaluate();
        } catch (RuntimeException e) {
            Supplier<R> retry = () -> tryEvaluate(request, append(previous, e));
            return handleException(
                    new FailureReportForNonVoidRequest<>(retry, previous, e)).execute();
        }
    }

    /**
     * Handles exceptions occurred during {@code RequestWithResult} execution and returns
     * an {@code ActionWithResult} that tells what to do next after the failure.
     */
    protected abstract <R> ActionWithResult<R> handleException(
            FailureReportForNonVoidRequest<R> failure);

    /**
     * Handles exceptions occurred during {@code VoidRequest} execution and returns
     * an {@code Action} that tells what to do next after the failure.
     */
    protected abstract Action handleException(FailureReport failure);

    /**
     * Summary of the {@code VoidRequest} failure.
     */
    protected static class FailureReport {

        private final Runnable retry;

        private final RuntimeException lastException;

        private final ImmutableList<RuntimeException> previousExceptions;

        private FailureReport(Runnable retry,
                              ImmutableList<RuntimeException> previousExceptions,
                              RuntimeException lastException) {
            this.retry = retry;
            this.previousExceptions = previousExceptions;
            this.lastException = lastException;
        }

        /**
         * Returns a predefined {@code Action} that retries failed {@code VoidRequest}.
         */
        public Action retry() {
            return retry::run;
        }

        /**
         * Returns a predefined {@code Action} that propagates current and previously occurred
         * exceptions as an {@code ExecutionFailedException}.
         */
        public Action propagate() {
            throw new ExecutionFailedException(append(previousExceptions, lastException));
        }

        /**
         * Returns last occurred exception.
         */
        public Exception lastException() {
            return lastException;
        }

        /**
         * Returns a list of all previously occurred exceptions.
         */
        public ImmutableList<RuntimeException> previousExceptions() {
            return previousExceptions;
        }
    }

    /**
     * An operation that is applied when {@code VoidRequest} failed.
     */
    protected interface Action {

        /**
         * Executes the action.
         */
        void execute();
    }

    /**
     * Summary of the {@code RequestWithResult} failure.
     */
    protected static class FailureReportForNonVoidRequest<R> {

        private final Supplier<R> retry;

        private final RuntimeException lastException;

        private final ImmutableList<RuntimeException> previousExceptions;

        private FailureReportForNonVoidRequest(Supplier<R> retry,
                                               ImmutableList<RuntimeException> previousExceptions,
                                               RuntimeException lastException) {
            this.retry = retry;
            this.previousExceptions = previousExceptions;
            this.lastException = lastException;
        }

        /**
         * Returns a predefined {@code ActionWithResult} that retries
         * failed {@code RequestWithResult}.
         */
        public ActionWithResult<R> retry() {
            return retry::get;
        }

        /**
         * Returns a predefined {@code ActionWithResult} that propagates current and previously
         * occurred exceptions as an {@code ExecutionFailedException}.
         */
        public ActionWithResult<R> propagate() {
            throw new ExecutionFailedException(append(previousExceptions, lastException));
        }

        /**
         * Returns last occurred exception.
         */
        public Exception lastException() {
            return lastException;
        }

        /**
         * Returns a list of all previously occurred exceptions.
         */
        public ImmutableList<RuntimeException> previousExceptions() {
            return previousExceptions;
        }
    }

    /**
     * An operation that is applied when {@code RequestWithResult} failed.
     */
    protected interface ActionWithResult<R> {

        /**
         * Executes the action.
         */
        R execute();
    }

    /**
     * Returns a list of the given {@code elements} withe appended {@code element}.
     */
    private static <T> ImmutableList<T> append(Iterable<T> elements, T element) {
        return ImmutableList.<T>builder()
                .addAll(elements)
                .add(element)
                .build();
    }
}
