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

package io.spine.delivery.client.strategy;

import com.google.common.collect.ImmutableList;

import java.util.function.Supplier;

/**
 * Summary of the {@code RequestWithResult} failure.
 *
 * @param <R>
 *         type of the result that has to be returned by the {@code RequestWithResult}.
 */
public final class FailedRequest<R> extends AbstractFailedRequest<ActionWithResult<R>> {

    private final Supplier<R> retry;

    /**
     * Creates a new {@code FailedRequest} with the given {@code retry} function and
     * previously occurred exceptions.
     *
     * @param retry
     *         function that will be retrying the original request.
     * @param allExceptions
     *         previously occurred exceptions.
     */
    FailedRequest(Supplier<R> retry, ImmutableList<RuntimeException> allExceptions) {
        super(allExceptions);
        this.retry = retry;
    }

    /**
     * Returns a predefined {@code ActionWithResult} that retries
     * failed {@code RequestWithResult}.
     */
    @Override
    public ActionWithResult<R> retry() {
        return retry::get;
    }
}
